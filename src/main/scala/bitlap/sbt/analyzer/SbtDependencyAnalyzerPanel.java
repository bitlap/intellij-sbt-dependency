package bitlap.sbt.analyzer;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import scala.collection.immutable.Seq$;
import scala.collection.mutable.ListBuffer;
import scala.collection.mutable.ListBuffer$;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

import scala.collection.immutable.List;

/**
 * @author 梦境迷离
 * @version 1.0, 2023/9/8
 */
@SuppressWarnings("unchecked")
public class SbtDependencyAnalyzerPanel {
    public JPanel mainPanel;

    public JComboBox<String> language;

    public JTextField organization;
    public JTextArea ignoreModules;
    public JCheckBox compileCheckBox;
    public JCheckBox providedCheckBox;
    public JCheckBox testCheckBox;
    private final SettingsState settings;

    public SbtDependencyAnalyzerPanel(SettingsState settings) {
        this.settings = settings;
    }

    boolean isModified() {
        boolean disableAnalyzeCompile = settings.getDisableAnalyzeCompile() == compileCheckBox.isSelected();
        boolean disableAnalyzeTest = settings.getDisableAnalyzeTest() == testCheckBox.isSelected();
        boolean disableAnalyzeProvided = settings.getDisableAnalyzeProvided() == providedCheckBox.isSelected();
        String[] ms = organization.getText().split(",");
        boolean ignoreModules = settings.getIgnoredModules().size() == ms.length &&
                isEq(ms, settings.getIgnoredModules());
        boolean org = settings.getOrganization() == null && organization.getText() == null ||
                (settings.getOrganization() != null && settings.getOrganization().equals(organization.getText()));
        boolean lang = SettingsState.toText(settings.getLanguageSelection()).equals(language.getSelectedItem());

        return !(lang && org && disableAnalyzeCompile && disableAnalyzeTest && disableAnalyzeProvided && ignoreModules);

    }

    private boolean isEq(String[] ms, List<String> list) {
        return list.forall(p -> Arrays.asList(ms).contains(p));
    }

    void apply() {
        // if data change, we publish a notification
        boolean changed = false;
        if (isModified()) {
            changed = true;
        }
        ListBuffer buffer = (ListBuffer) ListBuffer$.MODULE$.apply(Seq$.MODULE$.empty());
        Arrays.stream(ignoreModules.getText().split(",")).forEach(buffer::append);

        settings.setIgnoredModules(buffer.result());
        settings.setOrganization(organization.getText());
        settings.setLanguageSelection(SettingsState.fromText((String) language.getSelectedItem()));
        settings.setDisableAnalyzeCompile(compileCheckBox.isSelected());
        settings.setDisableAnalyzeTest(testCheckBox.isSelected());
        settings.setDisableAnalyzeProvided(providedCheckBox.isSelected());
        if (changed) {
            SettingsState.SettingsChangePublisher().onAnalyzerConfigurationChanged(settings);
        }
    }

    void from() {
        compileCheckBox.setSelected(settings.disableAnalyzeCompile());
        providedCheckBox.setSelected(settings.disableAnalyzeProvided());
        testCheckBox.setSelected(settings.disableAnalyzeTest());
        language.setSelectedItem(SettingsState.toText(settings.languageSelection()));
        organization.setText(settings.getOrganization());
        ignoreModules.setText(settings.getIgnoredModules().mkString(","));
    }


    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setAlignmentX(0.5f);
        mainPanel.setAlignmentY(0.5f);
        mainPanel.setAutoscrolls(false);
        final JLabel label1 = new JLabel();
        label1.setText("Language");
        mainPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(56, 17), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Ignore Scope");
        mainPanel.add(label2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(56, 17), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Ignore Modules");
        mainPanel.add(label3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(56, 17), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Organization");
        mainPanel.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(56, 17), null, 0, false));
        ignoreModules = new JTextArea();
        ignoreModules.setColumns(0);
        ignoreModules.setText("");
        mainPanel.add(ignoreModules, new GridConstraints(3, 1, 1, 3, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, -1), null, 0, false));
        language = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("en_US");
        defaultComboBoxModel1.addElement("zh_CN");
        language.setModel(defaultComboBoxModel1);
        mainPanel.add(language, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        organization = new JTextField();
        organization.setText("");
        mainPanel.add(organization, new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, -1), null, 0, false));
        compileCheckBox = new JCheckBox();
        compileCheckBox.setText("Compile");
        mainPanel.add(compileCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        providedCheckBox = new JCheckBox();
        providedCheckBox.setText("Provided");
        mainPanel.add(providedCheckBox, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        testCheckBox = new JCheckBox();
        testCheckBox.setText("Test");
        mainPanel.add(testCheckBox, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
