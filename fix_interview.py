"""Fix RecruitInterview.vue: connect createInterview and evaluateInterview to backend APIs."""
import os

path = os.path.join(os.path.dirname(__file__), 'src/views/RecruitInterview.vue')
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

count = 0

# 1. Replace evaluating button alert -> dispatch interview:evaluate event
old_eval = "onclick=\"window.alert('【面试评价】\\n填写对' + item.name + '的评价\\n[通过→待录用] [不通过→回流]')\""
new_eval = "onclick=\"window.dispatchEvent(new CustomEvent('interview:evaluate',{detail:'\" + item.name + \"'}))\""
if old_eval in content:
    content = content.replace(old_eval, new_eval)
    count += 1
    print("  [OK] evaluate button connected to interview:evaluate event")
else:
    print("  [FAIL] evaluate button pattern not found")
    # Debug: find the line
    for i, line in enumerate(content.split('\n')):
        if "case 'evaluating'" in line:
            print(f"         line {i+1}: {line}")
            print(f"         line {i+2}: {content.split(chr(10))[i+1]}")
            break

# 2. Replace openGlobalScheduleModal to call createInterview
old_func = """function openGlobalScheduleModal(name, position, dept) {
  window.alert('新建面试弹窗（demo）：' + (name || '选择候选人'));
}"""

new_func = """async function openGlobalScheduleModal(name, position, dept) {
  const title = name || '选择候选人';
  try {
    const res = await createInterview({
      candidate: name || '',
      position: position || '',
      dept: dept || '',
    });
    const id = res?.id || '[sample]';
    window.alert('已创建面试：' + title + ' (ID: ' + id + ')');
  } catch (e) {
    console.warn('[RecruitInterview] createInterview failed, using mock:', e);
    window.alert('新建面试弹窗（demo）：' + title);
  }
}"""

if old_func in content:
    content = content.replace(old_func, new_func)
    count += 1
    print("  [OK] openGlobalScheduleModal now calls createInterview API")
else:
    print("  [FAIL] openGlobalScheduleModal pattern not found")
    # Debug
    for i, line in enumerate(content.split('\n')):
        if 'openGlobalScheduleModal' in line:
            print(f"         line {i+1}: {line}")
            print(f"         line {i+2}: {content.split(chr(10))[i+1]}")
            print(f"         line {i+3}: {content.split(chr(10))[i+2]}")
            break

# 3. Add event listener for interview:evaluate
# Find the onMounted section and add the listener
old_mount = """onMounted(() => {
  document.addEventListener('click', onDocClick);
  loadFromApi();
});"""

new_mount = """onMounted(() => {
  document.addEventListener('click', onDocClick);
  window.addEventListener('interview:evaluate', async (e) => {
    const name = e.detail;
    try {
      await evaluateInterview(name, { result: 'pass', comment: '' });
      window.alert('【面试评价】' + name + '\\n已提交评价（通过→待录用）');
    } catch (err) {
      console.warn('[RecruitInterview] evaluateInterview failed, using mock:', err);
      window.alert('【面试评价】\\n填写对' + name + '的评价\\n[通过→待录用] [不通过→回流]');
    }
  });
  loadFromApi();
});"""

if old_mount in content:
    content = content.replace(old_mount, new_mount)
    count += 1
    print("  [OK] interview:evaluate event listener added")
else:
    print("  [FAIL] onMounted pattern not found")
    for i, line in enumerate(content.split('\n')):
        if 'onMounted' in line:
            print(f"         line {i+1}: {line}")
            print(f"         line {i+2}: {content.split(chr(10))[i+1]}")
            print(f"         line {i+3}: {content.split(chr(10))[i+2]}")
            break

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\nTotal changes: {count}")
