package bitlap.sbt.analyzer

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.jetbrains.sbt.project.SbtProjectSystem

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerGoToAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.project.DumbServiceBalloon
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

final class SbtDependencyAnalyzerGoToAction extends DependencyAnalyzerGoToAction(SbtProjectSystem.Id) {

  private val LOG = Logger.getInstance(classOf[SbtDependencyAnalyzerGoToAction])

  override def getNavigatable(e: AnActionEvent): Navigatable = {
    Option(getDeclaredDependency(e)).flatMap { dependency =>
      Try {
        val data = CommonDataKeys.PSI_ELEMENT.getData(dependency.getDataContext)
        Some(data.asInstanceOf[(_, _, _)]._1.asInstanceOf[PsiElement])
      }.getOrElse {
        LOG.error(s"Cannot get 'PSI_ELEMENT' as 'PsiElement' for ${dependency.getCoordinates}")
        None
      }
    }
      .map(psiElement => PsiNavigationSupport.getInstance().getDescriptor(psiElement))
      .orNull
  }

  private def getDeclaredDependency(e: AnActionEvent): DeclaredDependency = {
    val project    = e.getProject
    val dependency = e.getData(DependencyAnalyzerView.Companion.getDEPENDENCY)
    if (project == null || dependency == null) return null

    val coordinates = getUnifiedCoordinates(dependency)
    val module      = getParentModule(project, dependency)
    if (coordinates == null || module == null) return null

    val dependencyModifierService = DependencyModifierService.getInstance(project)
    dependencyModifierService.declaredDependencies(module).asScala.find(_.getCoordinates.equals(coordinates)).orNull
  }
}
