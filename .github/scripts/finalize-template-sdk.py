from pathlib import Path
import os
import subprocess

root = Path(os.environ['SDK_ROOT'])
file = root / 'jdt-ui/org.eclipse.jdt.ui/.settings/.api_filters'
text = file.read_text()
assert '<resource path="META-INF/MANIFEST.MF"' not in text
text = text.replace('<component id="org.eclipse.jdt.ui" version="2">', '''<component id="org.eclipse.jdt.ui" version="2">
    <!-- #3212: 3.40.100 deliberately distinguishes the repaired internal template
         registry ABI from already published 3.40.0 builds. SDK consumers need
         this service version as their minimum dependency. Remove this exception
         once the API baseline includes 3.40.100. -->
    <resource path="META-INF/MANIFEST.MF">
        <filter id="931135546">
            <message_arguments>
                <message_argument value="3.40.100"/>
                <message_argument value="3.39.0"/>
            </message_arguments>
        </filter>
    </resource>''')
file.write_text(text)
file = root / 'debug/org.eclipse.jdt.debug.tests/tests/org/eclipse/jdt/debug/tests/AutomatedSuite.java'
text = file.read_text().replace('import org.eclipse.jdt.debug.tests.ui.TemplateRegistryTests;\n', '')
assert 'import org.eclipse.jdt.debug.tests.ui.ViewManagementTests;' in text
text = text.replace('import org.eclipse.jdt.debug.tests.ui.ViewManagementTests;', 'import org.eclipse.jdt.debug.tests.ui.TemplateRegistryTests;\nimport org.eclipse.jdt.debug.tests.ui.ViewManagementTests;')
file.write_text(text)
for name in ['jdt-ui', 'debug', 'pde']:
    subprocess.run(['git', '-C', str(root/name), 'diff', '--check'], check=True)
    subprocess.run(['git', '-C', str(root/name), 'add', '-N', '.'], check=True)
    (root / 'patches' / (name + '.patch')).write_bytes(subprocess.check_output(['git', '-C', str(root/name), 'diff', '--binary']))
