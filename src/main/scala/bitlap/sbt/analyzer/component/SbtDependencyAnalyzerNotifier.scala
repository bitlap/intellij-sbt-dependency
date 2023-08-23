package bitlap.sbt.analyzer.component

import java.net.URL
import java.nio.file.Path

import bitlap.sbt.analyzer.{ SbtDependencyAnalyzerBundle, SbtDependencyAnalyzerIcons }

import com.intellij.notification.{ Notification, NotificationAction, NotificationGroupManager, NotificationType }
import com.intellij.openapi.actionSystem.{ AnAction, AnActionEvent }
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.fileEditor.{ FileDocumentManager, FileEditorManager, OpenFileDescriptor }
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

/** SbtDependencyAnalyzer global notifier
 */
object SbtDependencyAnalyzerNotifier {

  private lazy val GROUP =
    NotificationGroupManager.getInstance().getNotificationGroup("Sbt.DependencyAnalyzer.Notification")

  def addDependencyTreePlugin(project: Project): Unit = {
    // get project/plugins.sbt
    val projectPath    = VfsUtil.findFile(Path.of(project.getBasePath), true)
    val pluginsSbtFile = VfsUtil.findRelativeFile(projectPath, "project", "plugins.sbt")

    // add notification
    val notification = GROUP
      .createNotification(
        SbtDependencyAnalyzerBundle.message("sbt.dependency.analyzer.error.unknown"),
        SbtDependencyAnalyzerBundle.message("sbt.dependency.analyzer.error"),
        NotificationType.ERROR
      )
      .setIcon(SbtDependencyAnalyzerIcons.ICON)
    if (pluginsSbtFile != null) {
      notification.addAction(
        new NotificationAction(
          SbtDependencyAnalyzerBundle.message("sbt.dependency.analyzer.notification.goto.plugins.sbt")
        ) {
          override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
            val doc = FileDocumentManager.getInstance().getDocument(pluginsSbtFile)
            WriteCommandAction.runWriteCommandAction(
              project,
              new Runnable() {
                override def run(): Unit = {
                  doc.setReadOnly(false)
                  doc.setText(doc.getText + System.lineSeparator() + "addDependencyTreePlugin")
                  FileEditorManager
                    .getInstance(project)
                    .openTextEditor(new OpenFileDescriptor(project, pluginsSbtFile), true)
                  ProjectRefreshAction.Companion.refreshProject(project)
                }
              }
            )
          }
        }
      )
    }
    notification.notify(project)
  }
}
