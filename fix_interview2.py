"""Fix: replace the evaluate button alert with dispatchEvent in RecruitInterview.vue."""
import os

path = os.path.join(os.path.dirname(__file__), 'src/views/RecruitInterview.vue')
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find the evaluating case line
for i, line in enumerate(lines):
    if "case 'evaluating'" in line:
        old_line = lines[i + 1]
        # Build replacement
        # old: return resumeBtn + ' <button ... onclick="window.alert(\'【...')">填评价</button>';
        # new: return resumeBtn + ' <button ... onclick="window.dispatchEvent(new CustomEvent(\'interview:evaluate\',{detail:\'' + item.name + '\'}))">填评价</button>';

        new_line = old_line.replace(
            r"""onclick="window.alert(\'【面试评价】\\n填写对' + item.name + '的评价\\n[通过→待录用] [不通过→回流]\')" """,
            r"""onclick="window.dispatchEvent(new CustomEvent(\'interview:evaluate\',{detail:\'' + item.name + '\'}))" """
        )
        if new_line == old_line:
            print("FAIL: replace did not match for evaluate button")
            print(repr(old_line))
        else:
            lines[i + 1] = new_line
            print("OK: evaluate button connected to interview:evaluate event")
        break

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print("Done")
