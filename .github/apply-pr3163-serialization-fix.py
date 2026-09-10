from pathlib import Path

path = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/junit/tests/AbstractTestRunSessionSerializationTests.java')
text = path.read_text()

replacements = [
    ('Copyright (c) 2007, 2020 IBM Corporation and others.',
     'Copyright (c) 2007, 2026 IBM Corporation and others.'),
    ('import org.eclipse.jdt.internal.junit.model.JUnitModel;\nimport org.eclipse.jdt.internal.junit.model.TestRunSession;\n',
     'import org.eclipse.jdt.internal.junit.model.JUnitModel;\nimport org.eclipse.jdt.internal.junit.model.TestElement;\nimport org.eclipse.jdt.internal.junit.model.TestRunSession;\n'),
    ('\t\tPattern regex3= Pattern.compile("(?<=time=\\\\\")\\\\d+\\\\.\\\\d+(?=\\\\\")");\n'
     '\t\tString replacement= "";\n'
     '\t\texpected= regex3.matcher(regex2.matcher(regex.matcher(regex0.matcher(expected).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement);\n'
     '\t\tactual= regex3.matcher(regex2.matcher(regex.matcher(regex0.matcher(actual).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement);\n',
     '\t\tPattern regex3= Pattern.compile("(?<=time=\\\\\")\\\\d+\\\\.\\\\d+(?=\\\\\")");\n'
     '\t\t/*\n'
     '\t\t * CPU diagnostics are optional and VM-dependent. Their round-trip is verified below\n'
     '\t\t * against the imported model rather than against static XML fixtures.\n'
     '\t\t */\n'
     '\t\tPattern timingDetails= Pattern.compile("\\\\s+(?:cpuTime|userTime)=\\\\\"[^\\\\\"]*\\\\\"");\n'
     '\t\tString replacement= "";\n'
     '\t\texpected= regex3.matcher(regex2.matcher(regex.matcher(regex0.matcher(expected).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement);\n'
     '\t\tactual= regex3.matcher(regex2.matcher(regex.matcher(regex0.matcher(actual).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement)).replaceAll(replacement);\n'
     '\t\texpected= timingDetails.matcher(expected).replaceAll(replacement);\n'
     '\t\tactual= timingDetails.matcher(actual).replaceAll(replacement);\n'),
    ('\t\tassertEquals(expected.getTestResult(false), actual.getTestResult(false));\n'
     '\t\tFailureTrace expFailure= expected.getFailureTrace();\n',
     '\t\tassertEquals(expected.getTestResult(false), actual.getTestResult(false));\n'
     '\t\tTestElement expectedInternal= (TestElement) expected;\n'
     '\t\tTestElement actualInternal= (TestElement) actual;\n'
     '\t\tassertEquals(expectedInternal.getCpuTimeInSeconds(), actualInternal.getCpuTimeInSeconds(), 0.001d);\n'
     '\t\tassertEquals(expectedInternal.getUserCpuTimeInSeconds(), actualInternal.getUserCpuTimeInSeconds(), 0.001d);\n'
     '\t\tFailureTrace expFailure= expected.getFailureTrace();\n')
]

for old, new in replacements:
    count = text.count(old)
    print('matches', count, old.splitlines()[0][:100])
    if count != 1:
        raise SystemExit(f'expected one match, got {count}: {old[:120]!r}')
    text = text.replace(old, new, 1)

path.write_text(text)
