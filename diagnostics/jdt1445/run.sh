#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK=${1:?Usage: run.sh /path/to/eclipse-sdk [application-id]}
SDK=$(cd "$SDK" && pwd)
APP=${2:-jdt1445.diagnostics.run}
OUT="$HERE/out"
mkdir -p "$OUT/classes"
# Never implicitly recompile SDK/JUnit sources against the host JDK.
CP=$(find "$SDK/plugins" -name '*.jar' ! -name '*.source_*.jar' -printf '%p:' | sed 's/:$//')
find "$HERE/src" -name '*.java' -print > "$OUT/sources.txt"
javac --release 21 -sourcepath "$HERE/src" -implicit:none -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"
cp "$HERE/plugin.xml" "$OUT/classes/"
jar cfm "$SDK/plugins/jdt1445.diagnostics_1.0.0.jar" "$HERE/META-INF/MANIFEST.MF" -C "$OUT/classes" .
INFO="$SDK/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
sed -i '/^jdt1445.diagnostics,/d' "$INFO"
echo 'jdt1445.diagnostics,1.0.0,plugins/jdt1445.diagnostics_1.0.0.jar,4,false' >> "$INFO"
LAUNCHER=$(find "$SDK/plugins" -maxdepth 1 -name 'org.eclipse.equinox.launcher_*.jar' -print -quit)
java -jar "$LAUNCHER" -configuration "$SDK/configuration" -clean -nosplash -consolelog \
  -application "$APP" -data "$OUT/workspace-${APP##*.}"
