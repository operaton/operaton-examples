#!/usr/bin/env python3
"""
Make all examples self-contained by removing parent reference
and adding failsafe + rancher profile configuration.
"""
import subprocess
import sys
import re
from pathlib import Path


def transform_pom(pom_path):
    """Transform a single pom.xml file using xmllint and string manipulation."""

    # Read the file
    with open(pom_path, 'r') as f:
        content = f.read()

    # Step 1: Remove parent element if exists
    if '<parent>' in content:
        lines = content.split('\n')
        new_lines = []
        in_parent = False
        for line in lines:
            if '<parent>' in line:
                in_parent = True
                continue
            elif '</parent>' in line:
                in_parent = False
                continue
            elif not in_parent:
                new_lines.append(line)

        content = '\n'.join(new_lines)
        print(f"  ✓ Removed parent reference")

    # Step 2: Update/Add failsafe plugin configuration
    if 'maven-failsafe-plugin' in content:
        # Check if version exists
        has_version_356 = '<artifactId>maven-failsafe-plugin</artifactId>' in content and \
                          content.find('<version>3.5.6</version>') > content.find('<artifactId>maven-failsafe-plugin</artifactId>') - 500

        if not has_version_356:

            # Find the failsafe plugin block and update it
            # Pattern: from <plugin> containing maven-failsafe-plugin to </plugin>
            def replace_failsafe(match):
                plugin_block = match.group(0)

                # Check if it has a version
                if '<version>' not in plugin_block:
                    # Add version after artifactId
                    plugin_block = plugin_block.replace(
                        '<artifactId>maven-failsafe-plugin</artifactId>',
                        '<artifactId>maven-failsafe-plugin</artifactId>\n        <version>3.5.6</version>'
                    )
                else:
                    # Replace existing version
                    plugin_block = re.sub(
                        r'<version>[^<]+</version>',
                        '<version>3.5.6</version>',
                        plugin_block,
                        count=1
                    )

                # Check if it has executions
                if '<executions>' not in plugin_block:
                    # Add executions before </plugin>
                    executions_block = '''<executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
        '''
                    plugin_block = plugin_block.replace(
                        '      </plugin>',
                        f'{executions_block}      </plugin>'
                    )
                    plugin_block = plugin_block.replace(
                        '        </plugin>',
                        f'{executions_block}        </plugin>'
                    )

                return plugin_block

            content = re.sub(
                r'<plugin>\s*<groupId>org\.apache\.maven\.plugins</groupId>\s*<artifactId>maven-failsafe-plugin</artifactId>.*?</plugin>',
                replace_failsafe,
                content,
                flags=re.MULTILINE | re.DOTALL
            )
            print(f"  ✓ Updated failsafe plugin (v3.5.6)")
        else:
            print(f"  ⓘ Failsafe plugin already has version 3.5.6")
    else:
        # Add complete failsafe plugin to build/plugins section
        failsafe_plugin = '''      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <version>3.5.6</version>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>'''

        # Ensure build section exists
        if '<build>' not in content:
            build_plugins = f'''  <build>
    <plugins>
{failsafe_plugin}
    </plugins>
  </build>'''
            content = content.replace('</project>', f"{build_plugins}\n</project>")
        else:
            # Add to existing plugins section
            content = content.replace('    </plugins>', f"{failsafe_plugin}\n    </plugins>")

        print(f"  ✓ Added failsafe plugin (v3.5.6)")

    # Step 3: Add rancher profile if not exists
    if 'rancher' not in content or '<profile>' not in content:
        rancher_profile = '''  <profiles>
    <profile>
      <id>rancher</id>
      <activation>
        <file>
          <exists>${user.home}/.rd/docker.sock</exists>
        </file>
      </activation>
      <build>
        <pluginManagement>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-failsafe-plugin</artifactId>
              <configuration>
                <environmentVariables>
                  <DOCKER_HOST>unix://${user.home}/.rd/docker.sock</DOCKER_HOST>
                  <TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>/var/run/docker.sock</TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>
                  <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
                </environmentVariables>
              </configuration>
            </plugin>
          </plugins>
        </pluginManagement>
      </build>
    </profile>
  </profiles>'''
        content = content.replace('</project>', f"{rancher_profile}\n</project>")
        print(f"  ✓ Added rancher profile")
    else:
        print(f"  ⓘ Rancher profile already exists")

    # Step 4: Write the modified content back
    with open(pom_path, 'w') as f:
        f.write(content)

    # Step 5: Reformat with xmllint for proper indentation
    result = subprocess.run(
        ['xmllint', '--format', '-o', str(pom_path), str(pom_path)],
        capture_output=True,
        text=True
    )
    if result.returncode != 0:
        raise Exception(f"xmllint format failed: {result.stderr}")

    print(f"  ✓ Formatted and saved {pom_path.name}")


def main():
    """Process all example pom.xml files."""
    repo_root = Path(__file__).parent.parent
    examples_dir = repo_root / 'examples'

    # Find all pom.xml files in examples subdirectories
    pom_files = sorted(examples_dir.glob('**/pom.xml'))

    # Filter to only include example pom.xml files (not the aggregate one)
    pom_files = [f for f in pom_files if f.parent != repo_root]

    print(f"Found {len(pom_files)} example pom.xml files\n")

    success_count = 0
    for pom_path in pom_files:
        relative_path = pom_path.relative_to(repo_root)
        print(f"Processing: {relative_path}")
        try:
            transform_pom(pom_path)
            success_count += 1
        except Exception as e:
            print(f"  ✗ Error: {e}")
            sys.exit(1)

    print(f"\n✓ Successfully processed {success_count} pom.xml files")


if __name__ == '__main__':
    main()
