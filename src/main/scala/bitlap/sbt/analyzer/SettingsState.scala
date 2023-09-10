package bitlap
package sbt
package analyzer

import scala.beans.BeanProperty

import org.jetbrains.sbt.project.settings.*

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.settings.*
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient

import kotlin.jvm.Volatile

/** @author
 *    梦境迷离
 *  @version 1.0,2023/9/7
 */
@State(name = "SbtDependencyAnalyzer.Settings", storages = Array(new Storage("bitlap.sbt.dependency.analyzer.xml")))
@Service(Array(Service.Level.PROJECT))
final class SettingsState extends PersistentStateComponent[SettingsState] {

  import SettingsState.*

  @BeanProperty
  var disableAnalyzeCompile: Boolean = true

  @BeanProperty
  var disableAnalyzeProvided: Boolean = true

  @BeanProperty
  var disableAnalyzeTest: Boolean = true

  @BeanProperty
  var organization: String = _

  override def getState(): SettingsState = this

  override def loadState(state: SettingsState): Unit = {
    XmlSerializerUtil.copyBean(state, this)
  }

}

object SettingsState {

  val instance: SettingsState = ApplicationManager.getApplication.getService(classOf[SettingsState])

  val _Topic: Topic[SettingsChangeListener] =
    Topic.create("SbtDependencyAnalyzerSettingsChanged", classOf[SettingsChangeListener])

  trait SettingsChangeListener:

    def onAnalyzerConfigurationChanged(settingsState: SettingsState): Unit

  end SettingsChangeListener

  /** *
   *  {{{
   *     ApplicationManager
   *    .getApplication()
   *    .messageBus
   *    .connect(this)
   *    .subscribe(SettingsChangeListener.TOPIC, this)
   *  }}}
   */
  val SettingsChangePublisher: SettingsChangeListener =
    ApplicationManager.getApplication.getMessageBus.syncPublisher(_Topic)

}
