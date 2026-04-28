import os
import re
import glob

path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "resources", "Common", "UI", "Custom", "Pages", "TrailOfOrbis")

for filepath in glob.glob(os.path.join(path, "Node_*.ui")):
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        content = f.read()

    # Find #NodeBtn... pattern and remove underscores from it
    def fix_id(match):
        return match.group(0).replace('_', '')

    new_content = re.sub(r'#NodeBtn[a-zA-Z0-9_]+', fix_id, content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

    print(f"Fixed: {os.path.basename(filepath)}")

print("Done!")
