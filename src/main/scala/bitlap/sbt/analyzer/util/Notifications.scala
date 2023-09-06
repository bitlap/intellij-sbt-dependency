package bitlap.sbt.analyzer.util

import java.nio.file.Path

import scala.concurrent.Promise
import scala.concurrent.duration.*

import bitlap.sbt.analyzer.*
import bitlap.sbt.analyzer.activity.WhatsNew
import bitlap.sbt.analyzer.activity.WhatsNew.canBrowseInHTMLEditor

import org.jetbrains.plugins.scala.*
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectSystem

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.util.{ ExternalSystemBundle, ExternalSystemUtil }
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.{ DumbAwareAction, Project }
import com.intellij.openapi.vfs.VfsUtil

/** SbtDependencyAnalyzer global notifier
 */
object Notifications {

  private lazy val NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("Sbt.DependencyAnalyzer.Notification")

  private def getSdapText(project: Project): String = {
    val sbtVersion = Version(SbtUtils.getSbtVersion(project))
    val line = if (sbtVersion.major(2) >= Version("1.4")) {
      "addDependencyTreePlugin"
    } else {
      if (sbtVersion.major(3) >= Version("0.13.10")) {
        "addSbtPlugin(\"net.virtual-void\" % \"sbt-dependency-graph\" % \"0.9.2\")"
      } else {
        "addSbtPlugin(\"net.virtual-void\" % \"sbt-dependency-graph\" % \"0.8.2\")"
      }
    }
    "// -- This file was mechanically generated by Sbt Dependency Analyzer Plugin: Do not edit! -- //" + Constants.LineSeparator
      + line + Constants.LineSeparator
  }

  def notifyParseFileError(file: String): Unit = {
    // add notification when gets vfsFile timeout
    val notification = NotificationGroup
      .createNotification(
        SbtDependencyAnalyzerBundle.message("analyzer.task.error.title"),
        SbtDependencyAnalyzerBundle.message("analyzer.task.error.text", file),
        NotificationType.ERROR
      )
      .setIcon(SbtDependencyAnalyzerIcons.ICON)
    notification.notify(null)
  }

  def notifyUnknownError(project: Project, command: String, moduleId: String, scope: DependencyScopeEnum): Unit = {
    // add notification
    val notification = NotificationGroup
      .createNotification(
        SbtDependencyAnalyzerBundle.message("analyzer.task.error.title"),
        SbtDependencyAnalyzerBundle.message("analyzer.task.error.unknown.text", moduleId, scope.toString, command),
        NotificationType.ERROR
      )
      .setIcon(SbtDependencyAnalyzerIcons.ICON)
    notification.notify(project)
  }

  def notifyAndCreateSdapFile(project: Project): Unit = {
    // get project/plugins.sbt
    // val pluginSbtFileName = "plugins.sbt"
    val pluginSbtFileName = "sdap.sbt"
    val basePath          = VfsUtil.findFile(Path.of(project.getBasePath), true)

    WriteCommandAction.runWriteCommandAction(
      project,
      new Runnable() {
        override def run(): Unit = {
          // 1. get or create sdap.sbt file and add dependency tree statement
          val projectPath    = VfsUtil.createDirectoryIfMissing(basePath, "project")
          val pluginsSbtFile = projectPath.findOrCreateChildData(null, pluginSbtFileName)
          val doc            = FileDocumentManager.getInstance().getDocument(pluginsSbtFile)
          doc.setReadOnly(false)
          if (doc.getText == null || doc.getText.trim.isEmpty) {
            doc.setText(getSdapText(project))
          } else {
            doc.setText(doc.getText + Constants.LineSeparator + getSdapText(project))
          }
          // if intellij not enable auto-reload
          // force refresh project
          SbtUtils.refreshProject(project)
          SbtUtils.untilProjectReady(project)

          // 2. add notification
          val addNotification = NotificationGroup
            .createNotification(
              SbtDependencyAnalyzerBundle.message("analyzer.notification.addSdap.title"),
              SbtDependencyAnalyzerBundle.message("analyzer.notification.addSdap.text", pluginSbtFileName),
              NotificationType.INFORMATION
            )
            .setIcon(SbtDependencyAnalyzerIcons.ICON)
            .addAction(
              new NotificationAction(
                SbtDependencyAnalyzerBundle.message("analyzer.notification.gotoSdap", pluginSbtFileName)
              ) {
                override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
                  WriteCommandAction.runWriteCommandAction(
                    project,
                    new Runnable() {
                      override def run(): Unit = {
                        notification.expire()
                        val recheckFile = VfsUtil.findRelativeFile(basePath, "project", pluginSbtFileName)
                        if (recheckFile != null) {
                          FileEditorManager
                            .getInstance(project)
                            .openTextEditor(new OpenFileDescriptor(project, recheckFile), true)
                        }
                      }
                    }
                  )
                }
              }
            )
          addNotification.notify(project)
        }
      }
    )
  }

  /** notify information when update plugin
   */
  def notifyUpdateActivity(project: Project, version: Version, title: String, content: String): Unit = {
    val notification = NotificationGroup
      .createNotification(content, NotificationType.INFORMATION)
      .setTitle(title)
      .setImportant(true)
      .setIcon(SbtDependencyAnalyzerIcons.ICON)
      .setListenerIfSupport(NotificationListener.URL_OPENING_LISTENER)
    if (canBrowseInHTMLEditor) {
      notification.whenExpired(() => WhatsNew.browse(version, project))
    } else {
      notification.addAction(
        new DumbAwareAction(
          SbtDependencyAnalyzerBundle.message("analyzer.updated.notification.gotoBrowser"),
          null,
          AllIcons.General.Web
        ) {
          override def actionPerformed(e: AnActionEvent): Unit =
            notification.expire()
            BrowserUtil.browse(WhatsNew.getReleaseNotes(version))
        }
      )
    }
    notification.notify(project)
    if (canBrowseInHTMLEditor && SbtUtils.untilProjectReady(project)) {
      waitInterval(10.seconds)
      notification.expire()
    }
  }

  extension (notification: Notification) {

    private def setListenerIfSupport(listener: NotificationListener): Notification = {
      try {
        org.joor.Reflect.on(notification).call("setListener", listener)
      } catch {
        case _: Throwable =>
        // ignore
      }
      notification
    }
  }
}
