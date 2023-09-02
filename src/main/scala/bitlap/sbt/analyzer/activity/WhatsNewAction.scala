package bitlap
package sbt
package analyzer
package activity

import org.jetbrains.plugins.scala.project.Version

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

final class WhatsNewAction extends DumbAwareAction {

  getTemplatePresentation.setText(
    SbtDependencyAnalyzerBundle.message("analyzer.action.WhatsNew.text", "Sbt Dependency Analyzer")
  )

  override def actionPerformed(e: AnActionEvent): Unit = {
    WhatsNew.browse(Version(SbtDependencyAnalyzerPlugin.descriptor.getVersion), e.getProject)
  }
}
