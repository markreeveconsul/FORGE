"""Check documentation coverage and dated evidence without launching FORGE or reading secrets."""
from pathlib import Path
import json,re,hashlib,sys
ROOT=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
D=ROOT/'docs';failures=[]
def check(ok,message):
 print(('PASS ' if ok else 'FAIL ')+message)
 if not ok:failures.append(message)
items=json.loads((D/'uml/source-inventory.json').read_text(encoding='utf-8'))
paths={t['source'] for t in items};actual={p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/java').rglob('*.java')}
check(paths==actual,'Inventory covers every main Java source file')
check(all((ROOT/t['source']).exists() and hashlib.sha256((ROOT/t['source']).read_bytes()).hexdigest()==t['sourceSha256'] for t in items),'Inventory source hashes match current Java files')
tops={t['name'] for t in items if not t['nested']};index=json.loads((D/'uml/diagram-index.json').read_text())
homes=[n for sheet in index for n in sheet['classes']]
check(set(homes)==tops and len(homes)==len(tops),'Every top-level type has exactly one readable home sheet')
check(len(items)==165 and len(actual)==104,'Dated report type and file counts match the source snapshot')
for sheet in index:
 check(all((D/'uml'/(sheet['id']+suffix)).exists() for suffix in ['.puml','.svg','.png']),'Exports exist for '+sheet['id'])
 svg=(D/'uml'/(sheet['id']+'.svg')).read_text(encoding='utf-8')
 check('Syntax Error' not in svg and 'An error has occured' not in svg,'Renderer reported no syntax error for '+sheet['id'])
report=(D/'PROJECT-GUIDE.md').read_text(encoding='utf-8')
sections=['Project Title','Project Description','Problem Statement','Objectives','System Features','OOP Implementation','UML Class Diagram','GUI Screenshots','Testing','Conclusion']
check(all(f'## {i}. {s}' in report for i,s in enumerate(sections,1)),'All ten assignment sections are present')
check(all(s in report for s in ['Mark Reeve P. Consul','Sarigumba Generry','Blessy May Manila Aldaya','Melca Alegado','Aron Jef Tugahan','BSIT - 2 Section 06','Ms. Mary Clarence Bababtid']),'Cover includes the supplied group course and instructor')
check('@@' not in report,'Report has no unresolved authoring markers')
check(all((D/url).exists() for url in re.findall(r'!\[[^\]]*\]\(([^)]+)\)',report)),'Every report figure resolves to a local artifact')
check(not any(old in report for old in ['VisualProductSearch','Products are deactivated instead of deleted','Potential later work: FXML','Market is a single-store']),'Retired behavior and old project name removed from the report')
v=json.loads((D/'evidence/2026-10-24/verification.json').read_text())
check(sum(int(s['tests']) for s in v['suites'])==v['junitTests']==175,'JUnit summary totals 175 tests')
check(all(int(s[k])==0 for s in v['suites'] for k in ['failures','errors','skipped']),'JUnit evidence reports no failures errors or skipped tests')
check(sum(s['assertions'] for s in v['uiSuites'])==v['uiAssertions']==121,'UI evidence totals 121 assertions separately from JUnit')
check(v['liveAiRequests']==0,'Verification states that no live AI requests were made')
check('BUILD SUCCESS' in (D/'evidence/2026-10-24/maven-test.log').read_text(encoding='utf-8',errors='replace'),'Archived Maven run completed successfully')
# Only read documentation artifacts. Never open ignored configuration, private keys or store databases.
checked=0
for folder in [D/'uml',D/'evidence/2026-10-24',ROOT/'tools/documentation']:
 for p in folder.rglob('*'):
  if p.is_file() and p.suffix in ['.md','.json','.log','.puml','.py','.java']:
   text=p.read_text(encoding='utf-8-sig',errors='replace');checked+=1
   if re.search(r'AIza[0-9A-Za-z_-]{35}',text):failures.append('Potential API key in '+str(p.relative_to(ROOT)))
check(not any('Potential API key' in f for f in failures),f'No Google API key pattern in {checked} documentation and helper text artifacts')
try:
 from pypdf import PdfReader
 pdf=PdfReader(str(D/'submission/FORGE-PROJECT-REPORT.pdf'));text='\n'.join(p.extract_text() or '' for p in pdf.pages)
 check(all(f'{i}. {s}' in text for i,s in enumerate(sections,1)),'PDF text includes all ten sections')
 check(not re.search(r'AIza[0-9A-Za-z_-]{35}',text),'PDF text contains no Google API key pattern')
 check(len(PdfReader(str(D/'submission/FORGE-UML.pdf')).pages)==len(index)+1,'UML PDF includes the cover and every diagram sheet')
except ImportError:print('NOTE pypdf unavailable; run the PDF checks in the documentation environment.')
if failures:raise SystemExit('\n'.join(failures))
print('DOCUMENTATION CHECKS PASSED')
