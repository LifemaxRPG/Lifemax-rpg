#!/usr/bin/env python3
"""Compile/check/render the native GU pedestrian models without Android or network downloads.

Requires JDK 21 and Kotlin 2.x compiler dependencies as --compiler-classpath (or KOTLIN_JARS).
Runs the same StreetGU1Scenarios as the JUnit wrapper and renders production rig/raster pixels.
Only Ambient.hash is needed from the city: extract that exact source function into the temporary
compiler input, rather than copying or reimplementing its arithmetic here.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler-classpath', default=os.environ.get('KOTLIN_JARS'))
    parser.add_argument('--runtime-classpath', default=os.environ.get('KOTLIN_STDLIB'))
    parser.add_argument('--character', choices=['GU1', 'GU2', 'GU3', 'GU4', 'GU5'], default='GU1')
    parser.add_argument('--output', type=Path, default=Path('/tmp/gu-pedestrian-preview'))
    args = parser.parse_args()
    if not args.compiler_classpath:
        parser.error('provide a Kotlin 2.x compiler classpath')
    runtime = args.runtime_classpath or args.compiler_classpath
    root = Path(__file__).resolve().parents[1]
    main_src = root / 'android-wrapper/native/src/main/java/com/lifemax/rpg/feature/habits'
    output = args.output.resolve()
    with tempfile.TemporaryDirectory(prefix='street-cameo-') as temp:
        work = Path(temp)
        source = (main_src / 'domain/Ambient.kt').read_text()
        start = source.index('    fun hash(')
        body = source.index('{', start)
        depth, end = 1, body + 1
        while depth:
            if source[end] == '{':
                depth += 1
            elif source[end] == '}':
                depth -= 1
            end += 1
        ambient = work / 'AmbientHash.kt'
        ambient.write_text('package com.lifemax.rpg.feature.habits.domain\nobject Ambient {\n' +
                           source[start:end] + '\n}\n')
        files = [main_src / 'domain' / name for name in
                 ['PeopleRig.kt', 'Gait.kt', 'StreetGait.kt', 'StreetRig.kt', 'StreetCameos.kt',
                  'PeopleActivities.kt', 'StreetActs.kt', 'StreetManners.kt', 'StreetLayers.kt']]
        files += [main_src / 'presentation/scene' / name for name in
                  ['PeopleRaster.kt', 'PeopleSprites.kt', 'StreetRaster.kt']]
        files += [ambient, root / 'tools/preview/StreetCameoPreview.kt',
                  root / 'android-wrapper/native/src/test/java/com/lifemax/rpg/feature/habits/StreetGU1Scenarios.kt',
                  root / 'android-wrapper/native/src/test/java/com/lifemax/rpg/feature/habits/StreetGU2Scenarios.kt',
                  root / 'android-wrapper/native/src/test/java/com/lifemax/rpg/feature/habits/StreetGU3Scenarios.kt',
                  root / 'android-wrapper/native/src/test/java/com/lifemax/rpg/feature/habits/StreetGU4Scenarios.kt',
                  root / 'android-wrapper/native/src/test/java/com/lifemax/rpg/feature/habits/StreetGU5Scenarios.kt']
        sources = work / 'sources.txt'
        sources.write_text('\n'.join(json.dumps(str(p)) for p in files))
        classes = work / 'classes'
        subprocess.run(['java', '-cp', args.compiler_classpath,
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                        '-jvm-target', '21', '-classpath', runtime, '-d', str(classes), '@' + str(sources)],
                       check=True, timeout=180)
        command = ['java', '-Xmx1400m', '-Djava.awt.headless=true', '-cp',
                   str(classes) + os.pathsep + runtime]
        subprocess.run(command + ['com.lifemax.rpg.feature.habits.StreetGU1Scenarios'],
                       check=True, timeout=90)
        subprocess.run(command + ['com.lifemax.rpg.feature.habits.StreetGU2Scenarios'], check=True, timeout=90)
        subprocess.run(command + ['com.lifemax.rpg.feature.habits.StreetGU3Scenarios'], check=True, timeout=90)
        subprocess.run(command + ['com.lifemax.rpg.feature.habits.StreetGU4Scenarios'], check=True, timeout=90)
        subprocess.run(command + ['com.lifemax.rpg.feature.habits.StreetGU5Scenarios'], check=True, timeout=90)
        subprocess.run(command + ['com.lifemax.rpg.tools.StreetCameoPreview', str(output), args.character],
                       check=True, timeout=180)


if __name__ == '__main__':
    main()
