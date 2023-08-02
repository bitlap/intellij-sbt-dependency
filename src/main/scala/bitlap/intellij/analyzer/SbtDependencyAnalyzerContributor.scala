package bitlap.intellij.analyzer

import java.io.File
import java.util
import java.util.*
import java.util.concurrent.*

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

import org.jetbrains.sbt.{ SbtBundle, SbtUtil }
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.SbtTaskManager
import org.jetbrains.sbt.project.data.ModuleNode

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.dependencies.*
import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil

import kotlin.jvm.functions

/** @author
 *    梦境迷离
 *  @version 1.0,2023/8/1
 */
final class SbtDependencyAnalyzerContributor(project: Project) extends DependencyAnalyzerContributor {

  import SbtDependencyAnalyzerContributor.*

  private val projects              = ConcurrentHashMap[DependencyAnalyzerProject, ModuleNode]()
  private val configurationNodesMap = ConcurrentHashMap[String, util.List[DependencyScopeNode]]()
  private val dependencyMap         = ConcurrentHashMap[Long, Dependency]()

  override def getDependencies(
    externalProject: DependencyAnalyzerProject
  ): util.List[DependencyAnalyzerDependency] = {
    val moduleData = projects.get(externalProject)
    if (moduleData == null) Collections.emptyList()
    val scopeNodes = getOrRefreshData(moduleData)
    getDependencies(moduleData, scopeNodes)
  }

  override def getDependencyScopes(
    externalProject: DependencyAnalyzerProject
  ): util.List[DependencyAnalyzerDependency.Scope] = {
    val moduleData = projects.get(externalProject)
    if (moduleData == null) Collections.emptyList()
    getOrRefreshData(moduleData).asScala.map(_.toScope).asJava
  }

  override def getProjects: util.List[DependencyAnalyzerProject] = {
    if (projects.isEmpty) {
      val projectDataManager = ProjectDataManager.getInstance()
      projectDataManager.getExternalProjectsData(project, SbtProjectSystem.Id).asScala.foreach { projectInfo =>
        if (projectInfo.getExternalProjectStructure != null) {
          val projectStructure = projectInfo.getExternalProjectStructure
          ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.MODULE).asScala.foreach { moduleNode =>
            val moduleData = moduleNode.getData
            val module     = findModule(project, moduleData)
            if (module != null) {
              val externalProject = DAProject(module, moduleData.getModuleName)
              projects.put(externalProject, new ModuleNode(moduleData))
            }
          }

        }

      }

    }
    projects.keys.asScala.toList.asJava

  }

  override def whenDataChanged(listener: functions.Function0[kotlin.Unit], parentDisposable: Disposable): Unit = {
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(
      new ExternalSystemTaskNotificationListenerAdapter() {
        override def onEnd(id: ExternalSystemTaskId): Unit = {
          if (id.getType != ExternalSystemTaskType.RESOLVE_PROJECT) ()
          else if (id.getProjectSystemId != SbtProjectSystem.Id) ()
          else {
            projects.clear()
            configurationNodesMap.clear()
            dependencyMap.clear()
            listener.invoke()
          }
        }
      },
      parentDisposable
    )
  }

  private def getDependencies(
    moduleData: ModuleData,
    scopeNodes: util.List[DependencyScopeNode]
  ): util.List[Dependency] = {
    if (scopeNodes.isEmpty) return Collections.emptyList()
    val dependencies = ListBuffer[Dependency]()
    val root         = DAModule(moduleData.getModuleName)
    root.putUserData(Module_Data, moduleData)

    val rootDependency = DADependency(root, DefaultConfiguration, null, Collections.emptyList())
    dependencies.append(rootDependency)
    scopeNodes.asScala.view
      .map(sn => sn.toScope -> sn.getDependencies.asScala)
      .foreach((scope, dependencyList) =>
        dependencyList.foreach { dependencyNode =>
          addDependencies(rootDependency, scope, dependencyNode, dependencies, moduleData.getLinkedExternalProjectPath)
        }
      )
    dependencies.asJava
  }

  private def createDependency(
    dependencyNode: DependencyNode,
    scope: Dependency.Scope,
    usage: Dependency
  ): Dependency = {
    dependencyNode match
      case rn: ReferenceNode =>
        val dependency = dependencyMap.get(dependencyNode.getId)
        if (dependency == null) null
        else {
          DADependency(dependency.getData, scope, usage, dependency.getStatus)
        }
      case _ =>
        val dependencyData = dependencyNode.getDependencyData(projects)
        if (dependencyData == null) null
        else {
          val status = dependencyNode.getStatus(dependencyData)
          val dep    = DADependency(dependencyData, scope, usage, status)
          dependencyMap.put(dependencyNode.getId, dep)
          dep
        }
  }

  private def addDependencies(
    usage: Dependency,
    scope: Dependency.Scope,
    dependencyNode: DependencyNode,
    dependencies: ListBuffer[Dependency],
    projectDir: String
  ): Unit = {
    val dependency = createDependency(dependencyNode, scope, usage)
    if (dependency != null) {
      dependencies.append(dependency)
      for (node <- dependencyNode.getDependencies.asScala) {
        addDependencies(dependency, scope, node, dependencies, projectDir)
      }
    }

  }

  private def getOrRefreshData(moduleData: ModuleData): util.List[DependencyScopeNode] = {
    configurationNodesMap.computeIfAbsent(
      moduleData.getLinkedExternalProjectPath,
      _ => moduleData.loadDependencies(project)
    )
  }
}

object SbtDependencyAnalyzerContributor {

  private def scope(name: String): DAScope = DAScope(name, StringUtil.toTitleCase(name))

  final val DefaultConfiguration = scope("default")

  final val Module_Data = Key.create[ModuleData]("SbtDependencyAnalyzerContributor.ModuleData")

  extension (projectDependencyNode: ProjectDependencyNode) {

    def getModuleData(projects: ConcurrentHashMap[DependencyAnalyzerProject, ModuleNode]): ModuleData = {
      projects.values.asScala.map(_.data).find(_.getId == projectDependencyNode.getProjectPath).orNull
    }
  }

  extension (node: DependencyNode) {

    def getDependencyData(projects: ConcurrentHashMap[DependencyAnalyzerProject, ModuleNode]): Dependency.Data = {
      node match {
        case pdn: ProjectDependencyNode =>
          val data       = DAModule(pdn.getProjectName)
          val moduleData = pdn.getModuleData(projects)
          data.putUserData(Module_Data, moduleData)
          data
        case adn: ArtifactDependencyNode =>
          DAArtifact(adn.getGroup, adn.getModule, adn.getVersion)
        case _ => null
      }
    }

    def getStatus(data: Dependency.Data): util.List[Dependency.Status] = {
      val status = ListBuffer[Dependency.Status]()
      if (node.getResolutionState == ResolutionState.UNRESOLVED) {
        val message = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved")
        status.append(DAWarning(message))
      }
      val selectionReason = node.getSelectionReason
      if (
        data.isInstanceOf[Dependency.Data.Artifact] && selectionReason != null && selectionReason.startsWith(
          "between versions"
        )
      ) {
        val idx               = selectionReason.indexOf("and ")
        val conflictedVersion = selectionReason.substring(idx + 4)
        if (conflictedVersion.nonEmpty) {
          val message = ExternalSystemBundle.message(
            "external.system.dependency.analyzer.warning.version.conflict",
            conflictedVersion
          )
          status.append(DAWarning(message))
        }
      }
      status.asJava
    }
  }

  extension (dependencyScopeNode: DependencyScopeNode) {
    def toScope: DAScope = scope(dependencyScopeNode.getScope)
  }

  extension (moduleData: ModuleData) {

    def loadDependencies(project: Project): util.List[DependencyScopeNode] = {
      var dependencyScopeNodes = scala.List[DependencyScopeNode]()
      val sbtTaskManager       = new SbtTaskManager
      val directoryToRunTask   = moduleData.getProperty("directoryToRunTask")
      val sbtIdentityPath      = moduleData.getProperty("sbtIdentityPath")
      val outputFile           = "/target/dependencies-compile.graphml"
      sbtTaskManager.runCustomTask(
        project,
        "sbt.dependency.analyzer.loading",
        if (directoryToRunTask == null) moduleData.getLinkedExternalProjectPath else directoryToRunTask,
        if (sbtIdentityPath == null) SbtUtil.getLauncherDir.getAbsolutePath else sbtIdentityPath,
        ProgressExecutionMode.NO_PROGRESS_SYNC,
        new TaskCallback {
          override def onSuccess(): Unit = {
            val graphml = FileUtil.loadFile(new File(directoryToRunTask + outputFile))
            // TODO parse graphml
            val scopeNodes: scala.List[DependencyScopeNode] = ???
            dependencyScopeNodes = scopeNodes
          }

          override def onFailure(): Unit = {}
        }
      )
      dependencyScopeNodes.asJava
    }
  }
}
