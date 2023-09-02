package bitlap
package sbt
package analyzer
package activity

import bitlap.sbt.analyzer.*

import org.jetbrains.plugins.scala.project.Version

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI

/** @author
 *    梦境迷离
 *  @version 1.0,2023/9/1
 */
object PluginUpdateActivity:
  private val InitialVersion               = "0.0.0"
  private lazy val Log                     = Logger.getInstance(classOf[PluginUpdateActivity])
  private lazy val UpdateNotificationGroup = "Sbt.DependencyAnalyzer.UpdateNotification"
  private lazy val VersionProperty         = s"${SbtDependencyAnalyzerPlugin.PLUGIN_ID}.version"

  private class UrlAction(version: Version)
      extends DumbAwareAction(
        SbtDependencyAnalyzerBundle.message("analyzer.updated.notification.gotoBrowser"),
        null,
        AllIcons.General.Web
      ) {

    override def actionPerformed(e: AnActionEvent) = {

      BrowserUtil.browse(WhatsNew.getReleaseNotes(version))
    }
  }

end PluginUpdateActivity

final class PluginUpdateActivity extends BaseProjectActivity {

  import PluginUpdateActivity.*
  import WhatsNew.*

  override def onRunActivity(project: Project) = {
    checkUpdate(project)
  }

  private def checkUpdate(project: Project): Unit = {
    val plugin            = SbtDependencyAnalyzerPlugin.descriptor
    val versionString     = plugin.getVersion
    val properties        = PropertiesComponent.getInstance()
    val lastVersionString = properties.getValue(VersionProperty, InitialVersion)
    if (versionString == lastVersionString) {
      return
    }

    val version     = Version(versionString)
    val lastVersion = Version(lastVersionString)
    if (version == lastVersion) {
      return
    }

    // Simple handling of notifications
    val isNewVersion = version > lastVersion
    if (isNewVersion && showUpdateNotification(project, plugin, version)) {
      properties.setValue(VersionProperty, versionString)
    }
  }

  private def showUpdateNotification(
    project: Project,
    plugin: IdeaPluginDescriptor,
    version: Version
  ): Boolean = {
    val title = SbtDependencyAnalyzerBundle.message(
      "analyzer.updated.notification.title",
      plugin.getName,
      version.presentation
    )
    val partStyle = s"margin-top: ${JBUI.scale(8)}px;"
    val content = SbtDependencyAnalyzerBundle.message(
      "analyzer.updated.notification.text",
      partStyle,
      if (plugin.getChangeNotes == null) "<ul><li></li></ul>" else plugin.getChangeNotes,
      version.presentation
    )

    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(UpdateNotificationGroup)
    if (notificationGroup == null) return false

    val notification = notificationGroup
      .createNotification(content, NotificationType.INFORMATION)
      .setTitle(title)
      .setImportant(true)
      .setIcon(SbtDependencyAnalyzerIcons.ICON)

    if (!canBrowseInHTMLEditor) {
      notification.addAction(new UrlAction(version))
    } else {

      notification.whenExpired(() => BrowserUtil.browse(WhatsNew.getReleaseNotes(version)))
      waitInterval(10000)
      notification.expire()
    }

    notification.notify(project)

    true
  }
}
