"""Scores how well a model sorts spoken notes, using the debug-only AiEvalActivity.

score_sorting.py notes <out.txt>           writes the test notes, to push as ai-eval-notes.txt
score_sorting.py score <in.jsonl> <out.txt> scores the results the activity wrote
"""
import json
import pathlib
import sys

# (note, expected [(kind, words the title must contain, must have a deadline)])
CASES = [
    ("Call the bank tomorrow, and I have an idea, we could add a dark mode.", [("task", "bank", True), ("idea", "dark mode", False)]),
    ("I want to start a podcast about founders.", [("idea", "podcast", False)]),
    ("Start a newsletter for product managers.", [("idea", "newsletter", False)]),
    ("A referral program for our product could work well.", [("idea", "referral", False)]),
    ("I think we should launch a Hindi version of the site.", [("idea", "hindi", False)]),
    ("Thinking of moving to Bangalore next year.", [("idea", "bangalore", False)]),
    ("Why don't we add a pricing page?", [("idea", "pricing", False)]),
    ("Make a video on how SEO works.", [("idea", "video", False)]),
    ("Set a reminder to call mom tomorrow.", [("task", "call mom", True)]),
    ("Add a task to pay the rent by Friday.", [("task", "pay the rent", True)]),
    ("Can you remind me to call Ravi on Monday?", [("task", "call ravi", True)]),
    ("Please add buy milk to my list.", [("task", "milk", False)]),
    ("Note down that I need to renew my passport.", [("task", "renew my passport", False)]),
    ("My task is to finish the deck by Friday.", [("task", "finish the deck", True)]),
    ("I have a meeting with Ravi tomorrow at 3 pm.", [("task", "meeting", True)]),
    ("I have to go to the dentist on Saturday.", [("task", "dentist", True)]),
    ("You need to remind me about the insurance renewal.", [("task", "insurance", False)]),
    ("Today I need to send the report and there's an idea about automating it.", [("task", "report", True), ("idea", "automat", False)]),
    ("Okay so there are two things. First I need to call the CA about the GST filing. Second, I think we should launch a Hindi version of the site.", [("task", "gst", False), ("idea", "hindi", False)]),
    ("I need to call the bank tomorrow and one idea is to move the savings account to another bank.", [("task", "bank", True), ("idea", "savings", False)]),
    ("So I was just thinking that the onboarding is too long and maybe we can cut it down to two steps. Also I need to pay the electricity bill by the 5th.", [("idea", "onboarding", False), ("task", "electricity", True)]),
    ("Tomorrow I have to submit the report. Also the app could have a weekly summary.", [("task", "report", True), ("idea", "weekly summary", False)]),
    ("okay so tomorrow I have to pick up the car from service and also the landing page could use a better headline maybe something about AI and I should call Ravi about the contract on Thursday", [("task", "car", True), ("idea", "headline", False), ("task", "ravi", True)]),
    ("I don't want to forget the electricity bill it's due on the 10th and we should think about hiring an intern sometime next year", [("task", "electricity", True), ("idea", "intern", False)]),
    ("need to get groceries today milk eggs bread and I was wondering if we could do a podcast with customers", [("task", "groceries", True), ("idea", "podcast", False)]),
    ("what if the app had a daily digest email that would be nice also remember to renew the domain before the 20th", [("idea", "digest", False), ("task", "domain", True)]),
    ("Meeting with the investor next Tuesday at 11. Prepare the deck before that.", [("task", "investor", True), ("task", "deck", False)]),
    ("I really have to stop forgetting to take my medicine every morning", [("task", "medicine", False)]),
]
TUNED = len(CASES)
# Fresh notes, written after the prompt was final, so they show accuracy on things it wasn't adjusted for.
CASES += [
    ("Book the car service for Saturday morning and ask Vivek if he can send the invoice.", [("task", "car service", True), ("task", "invoice", False)]),
    ("Maybe we should add voice search to the app, I feel like people would use it a lot.", [("idea", "voice search", False)]),
    ("Get the passport photos done before the 15th.", [("task", "passport", True)]),
    ("I was reading about referral programs and I think something like that could work for our product.", [("idea", "referral", False)]),
    ("Transfer money to dad tomorrow and pay the credit card bill by the 25th.", [("task", "money", True), ("task", "credit card", True)]),
    ("Okay remind me to buy a birthday gift for Priya, her birthday is on Sunday.", [("task", "gift", True)]),
    ("One thing I want to try is writing a weekly blog about what we learn.", [("idea", "blog", False)]),
    ("Follow up with the CA about the tax notice. That's urgent.", [("task", "tax notice", False)]),
    ("Doctor's appointment on Monday at 10 am. Also we could move the standup to the afternoon.", [("task", "appointment", True), ("idea", "standup", False)]),
    ("call plumber today fix the kitchen tap and check if the water bill is paid", [("task", "plumber", True), ("task", "water bill", False)]),
    ("It would be great if the dashboard showed weekly trends.", [("idea", "dashboard", False)]),
    ("By end of the month I have to file the GST return, and I should renew the gym membership sometime.", [("task", "gst", True), ("task", "gym", False)]),
]


def score(items, expected):
    remaining = list(items)
    right = 0
    notes = []
    for kind, words, needs_date in expected:
        match = next((i for i in remaining if words in i["title"].lower()), None)
        if match is None:
            notes.append(f"MISSING {kind}:{words}")
            continue
        remaining.remove(match)
        good_kind = match["type"] == kind
        good_date = bool(match["due"]) == needs_date
        if good_kind and good_date:
            right += 1
        else:
            notes.append(f"WRONG {words}: {match['type']} due={match['due'] or 'none'}")
    if remaining:
        notes.append("EXTRA " + "; ".join(i["title"] for i in remaining))
    return right, notes


if sys.argv[1] == "notes":
    pathlib.Path(sys.argv[2]).write_text("\n".join(note for note, _ in CASES) + "\n", encoding="utf-8")
    raise SystemExit

rows = [json.loads(line) for line in pathlib.Path(sys.argv[2]).read_text(encoding="utf-8").splitlines() if line.strip()]
expected_by_note = dict(CASES)
fresh_notes = {note for note, _ in CASES[TUNED:]}
lines = []
total_right = total_expected = fresh_right = fresh_expected = 0
times = []
for row in rows:
    if "note" not in row:
        continue
    expected = expected_by_note[row["note"]]
    right, notes = score(row["items"], expected)
    total_right += right
    total_expected += len(expected)
    if row["note"] in fresh_notes:
        fresh_right += right
        fresh_expected += len(expected)
    times.append(row["ms"])
    lines.append(f"> {row['note']}")
    for item in row["items"]:
        lines.append(f"    {item['type']:4} | {item['title']}" + (f"  [{item['due']}]" if item["due"] else ""))
    lines.append(f"    {right}/{len(expected)}" + ("  " + " | ".join(notes) if notes else ""))
load = next((r["loadMs"] for r in rows if "loadMs" in r), "?")
done = any(r.get("done") for r in rows)
times.sort()
median = times[len(times) // 2] / 1000 if times else 0
lines.insert(0, f"SCORE {total_right}/{total_expected} over {len(times)} notes (fresh notes: {fresh_right}/{fresh_expected}), load {load} ms, median {median:.1f} s per note on emulator, finished={done}")
pathlib.Path(sys.argv[3]).write_text("\n".join(lines), encoding="utf-8")
print(lines[0])
