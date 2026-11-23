#!/usr/bin/env python3
"""
Make all examples self-contained by removing parent reference
and adding failsafe + rancher + colima profile configuration.

Uses regex-based XML transformation to work around expat issues.
"""
import subprocess
import sys
import re
from pathlib import Path


def remove_parent_element(content):
    """Remove parent element if exists."""
    if '<parent>' not in content:
        return content, False

    # Remove the entire parent block
    content = re.sub(
        r'\s*<parent>.*?</parent>\s*',
        '\n',
        content,
        flags=re.DOTALL
    )
    return content, True


def has_execution_block_for_failsafe(content):
    """Check if failsafe plugin has at least one execution block using regex."""
    # Find the failsafe plugin block
    failsafe_match = re.search(
        r'<plugin>\s*<groupId>org\.apache\.maven\.plugins</groupId>\s*'
        r'<artifactId>maven-failsafe-plugin</artifactId>.*?</plugin>',
        content,
        re.DOTALL
    )
    if not failsafe_match:
        return False, None

    plugin_block = failsafe_match.group(0)
    has_exec = '<executions>' in plugin_block
    return has_exec, plugin_block


def deduplicate_executions_in_plugin_block(plugin_block):
    """Remove duplicate execution blocks from a plugin, keeping only one."""
    # Find all <executions> blocks
    exec_blocks = re.findall(r'<executions>.*?</executions>', plugin_block, re.DOTALL)

    if len(exec_blocks) <= 1:
        return plugin_block, 0

    # Keep only the first one, remove the rest
    deduplicated = plugin_block
    for dup_block in exec_blocks[1:]:
        deduplicated = deduplicated.replace(dup_block, '', 1)

    return deduplicated, len(exec_blocks) - 1


def ensure_failsafe_plugin(content):
    """Ensure failsafe plugin with version and executions exists."""
    modified = False
    message = ""

    has_exec, plugin_block = has_execution_block_for_failsafe(content)

    if plugin_block:
        # Failsafe plugin exists - update it
        # First, deduplicate any execution blocks
        deduplicated_block, dup_count = deduplicate_executions_in_plugin_block(plugin_block)
        if dup_count > 0:
            content = content.replace(plugin_block, deduplicated_block, 1)
            plugin_block = deduplicated_block
            message += f"✓ Removed {dup_count} duplicate execution block(s) "
            modified = True

        # Ensure version 3.5.6
        version_match = re.search(r'<version>.*?</version>', plugin_block)
        if version_match:
            old_version = version_match.group(0)
            if old_version != '<version>3.5.6</version>':
                new_block = plugin_block.replace(old_version, '<version>3.5.6</version>', 1)
                content = content.replace(plugin_block, new_block, 1)
                plugin_block = new_block
                message += "✓ Updated failsafe version "
                modified = True
        else:
            # Add version after artifactId
            new_block = plugin_block.replace(
                '<artifactId>maven-failsafe-plugin</artifactId>',
                '<artifactId>maven-failsafe-plugin</artifactId>\n        <version>3.5.6</version>',
                1
            )
            content = content.replace(plugin_block, new_block, 1)
            plugin_block = new_block
            message += "✓ Added failsafe version "
            modified = True

        # Ensure executions block exists
        if not has_exec:
            executions_block = '''<executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
        '''
            # Add before </plugin>
            new_block = plugin_block.replace('      </plugin>', f'{executions_block}      </plugin>', 1)
            if new_block == plugin_block:
                new_block = plugin_block.replace('        </plugin>', f'{executions_block}        </plugin>', 1)

            if new_block != plugin_block:
                content = content.replace(plugin_block, new_block, 1)
                message += "✓ Added execution block "
                modified = True

        if message:
            message = "✓ Updated failsafe plugin (v3.5.6) " + message.strip()
        else:
            message = "ⓘ Failsafe plugin already correct"
    else:
        # Create failsafe plugin from scratch
        failsafe_plugin = '''<plugin>
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

        # Ensure build/plugins section exists
        if '<build>' not in content:
            build_section = f'''<build>
    <plugins>
{failsafe_plugin}
    </plugins>
  </build>'''
            content = content.replace('</project>', f"\n  {build_section}\n</project>")
        elif '<plugins>' not in content:
            plugins_section = f'''<plugins>
{failsafe_plugin}
    </plugins>'''
            build_end = content.find('</build>')
            content = content[:build_end] + f"\n    {plugins_section}\n  " + content[build_end:]
        else:
            # Add to existing plugins
            content = content.replace(
                '    </plugins>',
                f'{failsafe_plugin}\n    </plugins>',
                1
            )

        message = "✓ Added failsafe plugin (v3.5.6)"
        modified = True

    return content, modified, message


def has_rancher_profile(content):
    """Check if a rancher profile with id=rancher exists (exact match)."""
    # Look for <profile> containing <id>rancher</id> (exact match, not rancher-desktop)
    profile_match = re.search(
        r'<profile>.*?<id>rancher</id>.*?</profile>',
        content,
        re.DOTALL
    )
    return profile_match is not None


def has_colima_profile(content):
    """Check if a colima profile with id=colima exists (exact match)."""
    # Look for <profile> containing <id>colima</id>
    profile_match = re.search(
        r'<profile>.*?<id>colima</id>.*?</profile>',
        content,
        re.DOTALL
    )
    return profile_match is not None


def ensure_rancher_profile(content):
    """Ensure rancher profile exists with correct configuration."""
    # First, remove any old rancher-related profiles that aren't the standard "rancher" one
    # This handles the case where old profiles like "rancher-desktop" or "docker-alternative-rancher" exist
    content = re.sub(
        r'<profile>\s*(?:<!--.*?-->)?\s*<id>(?!rancher</id>)[^<]*rancher[^<]*</id>.*?</profile>',
        '',
        content,
        flags=re.DOTALL
    )

    # Remove any empty or duplicate <profiles> sections
    # Merge all profiles into a single section at the end
    all_profiles_content = []
    profiles_matches = list(re.finditer(r'<profiles>(.*?)</profiles>', content, re.DOTALL))

    if profiles_matches:
        # Collect all profile content from all <profiles> sections
        for match in profiles_matches:
            profiles_content = match.group(1)
            all_profiles_content.append(profiles_content)

        # Remove all old <profiles> sections
        for match in reversed(profiles_matches):  # reverse to maintain positions
            content = content[:match.start()] + content[match.end():]

    # Check if rancher profile exists
    rancher_exists = False
    for prof_content in all_profiles_content:
        if '<id>rancher</id>' in prof_content:
            rancher_exists = True
            break

    if not rancher_exists:
        # Add the rancher profile to the collected content
        all_profiles_content.append('''
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
    </profile>''')
        message = "✓ Added rancher profile"
    else:
        message = "ⓘ Rancher profile already exists"

    # Write merged profiles back (if any content exists)
    if all_profiles_content:
        merged = ''.join(all_profiles_content)
        profiles_block = f'''<profiles>{merged}
  </profiles>'''
        content = content.replace('</project>', f"{profiles_block}\n</project>")

    return content, len(profiles_matches) > 0 or not rancher_exists, message


def ensure_colima_profile(content):
    """Ensure colima profile exists within the <profiles> section."""
    # Check if colima profile exists
    colima_exists = has_colima_profile(content)

    if not colima_exists:
        # Find the <profiles> closing tag
        profiles_close_match = re.search(r'</profiles>', content)

        if profiles_close_match:
            # Add colima profile inside existing <profiles> section
            colima_profile = '''
    <profile>
      <id>colima</id>
      <activation>
        <file>
          <exists>${user.home}/.colima/default/docker.sock</exists>
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
                  <DOCKER_HOST>unix://${user.home}/.colima/default/docker.sock</DOCKER_HOST>
                  <TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>${user.home}/.colima/default/docker.sock</TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>
                  <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
                </environmentVariables>
              </configuration>
            </plugin>
          </plugins>
        </pluginManagement>
      </build>
    </profile>'''
            content = content.replace('</profiles>', f"{colima_profile}\n  </profiles>", 1)
            message = "✓ Added colima profile"
        else:
            # No <profiles> section exists, which shouldn't happen if rancher was added
            message = "⚠ Could not find profiles section"
    else:
        message = "ⓘ Colima profile already exists"

    return content, not colima_exists, message


def transform_pom(pom_path):
    """Transform a single pom.xml file."""

    # Read the file
    with open(pom_path, 'r') as f:
        content = f.read()

    # Step 1: Remove parent element if exists
    content, removed_parent = remove_parent_element(content)
    if removed_parent:
        print(f"  ✓ Removed parent reference")

    # Step 2: Ensure failsafe plugin configuration
    content, modified_failsafe, failsafe_msg = ensure_failsafe_plugin(content)
    print(f"  {failsafe_msg}")

    # Step 3: Ensure rancher profile
    content, added_profile, profile_msg = ensure_rancher_profile(content)
    print(f"  {profile_msg}")

    # Step 4: Ensure colima profile
    content, added_colima, colima_msg = ensure_colima_profile(content)
    print(f"  {colima_msg}")

    # Step 5: Write the modified content back
    with open(pom_path, 'w') as f:
        f.write(content)

    # Step 6: Reformat with xmllint for proper indentation
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
