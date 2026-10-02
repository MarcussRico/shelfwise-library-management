"""Independent check of the ISBN repair: compares each repaired ISBN-10 with the
dataset's own (float-rounded) isbn13 column and writes the counts into results.json."""
import csv, json


def v10(s):
    if len(s) != 10 or not (s[:9].isdigit() and (s[9].isdigit() or s[9] == 'X')):
        return False
    return sum((10 if (i == 9 and c == 'X') else int(c)) * (10 - i) for i, c in enumerate(s)) % 11 == 0


def to13(p):
    core = '978' + p[:9]
    s = sum(int(c) * (1 if i % 2 == 0 else 3) for i, c in enumerate(core))
    return int(core + str((10 - s % 10) % 10))


agree = dis = unchecked = 0
for r in csv.DictReader(open('data/books.csv')):
    raw = r['isbn'].strip().upper()
    if v10(raw) or not raw or len(raw) >= 10:
        continue
    p = raw.rjust(10, '0')
    if not v10(p):
        continue
    try:
        f = float(r['isbn13'])
    except ValueError:
        unchecked += 1
        continue
    sig = len(r['isbn13'].split('e')[0].replace('.', '')) - 1
    if abs(to13(p) - f) <= 0.5 * 10 ** (12 - sig) + 1:
        agree += 1
    else:
        dis += 1
R = json.load(open('results/results.json'))
R['catalogue']['crosscheck'] = {'agree': agree, 'disagree': dis, 'unchecked': unchecked}
json.dump(R, open('results/results.json', 'w'), indent=1)
print(agree, dis, unchecked)
