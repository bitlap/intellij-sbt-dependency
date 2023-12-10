package bitlap
package sbt
package analyzer
package action

import bitlap.sbt.analyzer.util.SbtUtils

import org.jetbrains.sbt.project.SbtProjectSystem

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil

final class SbtRefreshDependenciesAction extends BaseRefreshDependenciesAction:

  override lazy val eventText: String = SbtDependencyAnalyzerBundle.message("analyzer.refresh.dependencies.text")

  override lazy val eventDescription: String =
    SbtDependencyAnalyzerBundle.message("analyzer.refresh.dependencies.description")

  override def actionPerformed(e: AnActionEvent): Unit = {
    SbtDependencyAnalyzerContributor.isAvailable.set(false)
    SbtUtils.refreshProject(e.getProject)
  }

end SbtRefreshDependenciesAction
