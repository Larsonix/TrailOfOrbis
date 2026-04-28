"""Add blank lines inside code blocks for visual breathing room."""
import os
import glob

wiki_dir = 'Wiki/content/Larsonix_TrailOfOrbis'
fence = '```'
count = 0

for filepath in glob.glob(os.path.join(wiki_dir, '**/*.md'), recursive=True):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    original = lines[:]
    result = []
    in_block = False
    block_start = -1

    for i, line in enumerate(lines):
        stripped = line.rstrip('\n').rstrip('\r')

        if stripped.startswith(fence) and not in_block:
            # Opening fence
            in_block = True
            block_start = len(result)
            result.append(line)
            # Add blank line after opening if next line is not blank
            if i + 1 < len(lines) and lines[i + 1].strip() != '':
                result.append('\n')
        elif stripped == fence and in_block:
            # Closing fence
            in_block = False
            # Add blank line before closing if previous line is not blank
            if result and result[-1].strip() != '':
                result.append('\n')
            result.append(line)
        else:
            result.append(line)

    if result != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(result)
        count += 1
        print(f'  Updated: {filepath}')

print(f'\nTotal: {count} files updated')
