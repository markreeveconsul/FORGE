import sys,re,json,io,html,os
from pathlib import Path
if os.environ.get('FORGE_DOCS_VENDOR'):sys.path.insert(0,os.environ['FORGE_DOCS_VENDOR'])
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,Table,TableStyle,PageBreak,Preformatted,KeepTogether
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet,ParagraphStyle
from reportlab.lib.pagesizes import A4,A3,landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.graphics import renderPDF
from svglib.svglib import svg2rlg
from pypdf import PdfReader,PdfWriter
from PIL import Image
ROOT=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
D=ROOT/'docs';WORK=ROOT/'target/documentation';WORK.mkdir(parents=True,exist_ok=True)
OUT=D/'submission';OUT.mkdir(parents=True,exist_ok=True);PARTS=WORK/'pdf-parts';PARTS.mkdir(exist_ok=True)
for name,file in [('Body','arial.ttf'),('BodyBold','arialbd.ttf'),('BodyItalic','ariali.ttf')]:pdfmetrics.registerFont(TTFont(name,str(Path('C:/Windows/Fonts')/file)))
pdfmetrics.registerFont(TTFont('ArialUnicode','C:/Windows/Fonts/arial.ttf'))
pdfmetrics.registerFontFamily('Body',normal='Body',bold='BodyBold',italic='BodyItalic',boldItalic='BodyBold')
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyReport',fontName='Body',fontSize=10.1,leading=14.2,spaceAfter=7,textColor=colors.black))
styles.add(ParagraphStyle(name='ReportTitle',fontName='BodyBold',fontSize=27,leading=33,spaceAfter=16,textColor=colors.black))
styles.add(ParagraphStyle(name='ReportHeading',fontName='BodyBold',fontSize=17,leading=22,spaceBefore=15,spaceAfter=11,keepWithNext=True,textColor=colors.black))
styles.add(ParagraphStyle(name='ReportSub',fontName='BodyBold',fontSize=12.2,leading=17,spaceBefore=12,spaceAfter=7,keepWithNext=True,textColor=colors.black))
styles.add(ParagraphStyle(name='TableBody',fontName='Body',fontSize=8.7,leading=12,spaceAfter=0))
styles.add(ParagraphStyle(name='TableHead',fontName='BodyBold',fontSize=8.7,leading=12,textColor=colors.white,spaceAfter=0))
styles.add(ParagraphStyle(name='CaptionReport',fontName='Body',fontSize=9.5,leading=14,textColor=colors.HexColor('#4D5661')))
styles.add(ParagraphStyle(name='BulletReport',parent=styles['BodyReport'],leftIndent=13,firstLineIndent=-10,spaceAfter=6))

def inline(s):
 s=html.escape(s)
 s=re.sub(r'\*\*(.*?)\*\*',r'<b>\1</b>',s)
 s=re.sub(r'`(.*?)`',r'<font name="Courier" size="9">\1</font>',s)
 s=re.sub(r'\[(.*?)\]\((.*?)\)',r'\1',s)
 return s

def markdown_story(text):
 lines=text.splitlines();story=[];i=0;paragraph=[]
 def flush():
  if paragraph:story.append(Paragraph(inline(' '.join(paragraph)),styles['BodyReport']));paragraph.clear()
 while i<len(lines):
  line=lines[i]
  if not line.strip():flush();i+=1;continue
  if line.startswith('```'):
   flush();code=[];i+=1
   while i<len(lines) and not lines[i].startswith('```'):code.append(lines[i]);i+=1
   # Wrap long code lines without changing the editable report source.
   wrapped=[]
   for c in code:
    while len(c)>91:wrapped.append(c[:91]);c='    '+c[91:]
    wrapped.append(c)
   story += [Spacer(1,6),Preformatted('\n'.join(wrapped),ParagraphStyle('Code',fontName='Courier',fontSize=8.1,leading=11)),Spacer(1,10)];i+=1;continue
  if line.startswith('|'):
   flush();rows=[]
   while i<len(lines) and lines[i].startswith('|'):
    cells=[x.strip() for x in lines[i].strip('|').split('|')]
    if not all(re.fullmatch(r':?-+:?',x) for x in cells):rows.append(cells)
    i+=1
   n=len(rows[0]);width=483
   widths=([width*.56]+[width*.44/(n-1)]*(n-1)) if n>2 else [width*.38,width*.62]
   data=[[Paragraph(inline(c),styles['TableHead' if r==0 else 'TableBody']) for c in row] for r,row in enumerate(rows)]
   table=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT')
   table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#243244')),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#F4F6F8')]),('GRID',(0,0),(-1,-1),.5,colors.HexColor('#D9D9D9')),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8),('VALIGN',(0,0),(-1,-1),'MIDDLE')]))
   story += [table,Spacer(1,13)];continue
  if line.startswith('# '):flush();story.append(Paragraph(inline(line[2:]),styles['ReportTitle']));i+=1;continue
  if line.startswith('## '):
   flush()
   if line.startswith(('## 2.','## 5.','## 6.','## 10.')):story.append(PageBreak())
   story.append(Paragraph(inline(line[3:]),styles['ReportHeading']));i+=1;continue
  if line.startswith('### '):
   flush()
   if line=='### FORGE AI':story.append(PageBreak())
   story.append(Paragraph(inline(line[4:]),styles['ReportSub']));i+=1;continue
  if line.startswith('- '):flush();story.append(Paragraph('&#8226; '+inline(line[2:]),styles['BulletReport']));i+=1;continue
  if line.startswith('!['):flush();i+=1;continue
  paragraph.append(line.strip());i+=1
 flush();return story

def text_pdf(name,text):
 path=PARTS/(name+'.pdf');doc=SimpleDocTemplate(str(path),pagesize=A4,leftMargin=56,rightMargin=56,topMargin=49,bottomMargin=47,title='FORGE project documentation',author='FORGE project group')
 story=markdown_story(text)
 while story and isinstance(story[0],PageBreak):story.pop(0)
 doc.build(story);return path

def paragraph_on(c,text,x,y,width,style):
 p=Paragraph(inline(text),style);_,h=p.wrap(width,1000);p.drawOn(c,x,y-h);return y-h

def diagram_pdf(item,num):
 path=PARTS/(item['id']+'.pdf');dw=svg2rlg(str(D/'uml'/(item['id']+'.svg')))
 size=landscape(A3);w,h=size;c=canvas.Canvas(str(path),pagesize=size)
 y=paragraph_on(c,'UML sheet '+str(num)+' '+item['title'],38,h-32,w-76,styles['ReportHeading'])
 y=paragraph_on(c,'Section 7  |  FORGE class diagram  |  Declared API excerpts; full signatures in the companion class reference',38,y-3,w-76,styles['CaptionReport'])
 maxw,maxh=w-76,y-62;scale=min(maxw/dw.width,maxh/dw.height,1.55)
 c.saveState();c.translate(38+(maxw-dw.width*scale)/2,45+(maxh-dw.height*scale)/2);c.scale(scale,scale);renderPDF.draw(dw,c,0,0);c.restoreState();c.showPage();c.save();return path

def screenshot_pdf(item,num):
 path=PARTS/(f'screen-{num:02}.pdf');w,h=landscape(A4);c=canvas.Canvas(str(path),pagesize=(w,h))
 y=paragraph_on(c,'Figure '+str(num)+' '+item['title'],35,h-25,w-70,styles['ReportHeading'])
 y=paragraph_on(c,'Section 8  |  Actual JavaFX scene  |  Isolated verification data  |  24 October 2026',35,y-4,w-70,styles['CaptionReport'])
 image=ROOT/item['file'];iw,ih=Image.open(image).size
 capheight=65;maxw,maxh=w-70,y-22-capheight-35;scale=min(maxw/iw,maxh/ih)
 x=35+(maxw-iw*scale)/2;bottom=y-15-ih*scale
 c.drawImage(str(image),x,bottom,width=iw*scale,height=ih*scale)
 c.setStrokeColor(colors.HexColor('#D9D9D9'));c.setLineWidth(.5);c.rect(x,bottom,iw*scale,ih*scale)
 paragraph_on(c,item['caption']+' Verification: '+item['suite']+'.',35,bottom-10,w-70,styles['CaptionReport'])
 c.showPage();c.save();return path

report=(D/'PROJECT-GUIDE.md').read_text(encoding='utf-8')
pre,rest=report.split('## 7. UML Class Diagram',1);seven,rest=rest.split('## 8. GUI Screenshots',1);eight,last=rest.split('## 9. Testing',1)
seven_intro=seven.split('### UML sheet',1)[0];eight_intro=eight.split('### Figure',1)[0]
index=json.loads((D/'uml/diagram-index.json').read_text());shots=json.loads((D/'evidence/2026-10-24/screenshots.json').read_text())
parts=[text_pdf('front',pre),text_pdf('uml-intro','## 7. UML Class Diagram'+seven_intro)]
diagpaths=[diagram_pdf(d,i) for i,d in enumerate(index,1)];parts+=diagpaths
parts+=[text_pdf('screens-intro','## 8. GUI Screenshots'+eight_intro)]
parts += [screenshot_pdf(s,i) for i,s in enumerate(shots,1)]
parts += [text_pdf('testing-conclusion','## 9. Testing'+last)]
raw=PdfWriter()
for p in parts:raw.append(str(p))
# Determine real section pages before inserting a printed contents page.
sections={}
for i,page in enumerate(raw.pages):
 text=page.extract_text() or ''
 for match in re.finditer(r'(?m)^([1-9]|10)\. (Project Title|Project Description|Problem Statement|Objectives|System Features|OOP Implementation|UML Class Diagram|GUI Screenshots|Testing|Conclusion)\s*$',text):sections[int(match[1])]=(match[2],i)
assert len(sections)==10,sections
contents=['# Contents','']
for n,(title,page) in sorted(sections.items()):contents.append(f'**{n}. {title}** - page {page+1+(1 if page>=1 else 0)}\n')
contents += ['','UML sheets are landscape A3 so class names and operations remain readable. Interface screenshots are landscape A4. SVG diagrams and editable Markdown accompany the PDFs.','The evidence is a dated snapshot. Later code changes require regenerated diagrams and relevant tests.']
toc=text_pdf('contents','\n'.join(contents));assert len(PdfReader(str(toc)).pages)==1
raw.insert_page(PdfReader(str(toc)).pages[0],1)
# Add sequential page numbers after mixed-size pages have been assembled.
for i,page in enumerate(raw.pages):
 width,height=float(page.mediabox.width),float(page.mediabox.height);buffer=io.BytesIO();c=canvas.Canvas(buffer,pagesize=(width,height));c.setFont('Body',8);c.setFillColor(colors.HexColor('#69727C'));c.drawString(35,22,'FORGE  |  Object-Oriented Programming  |  BSIT - 2 Section 06');c.drawRightString(width-35,22,f'{i+1} / {len(raw.pages)}');c.save();page.merge_page(PdfReader(buffer).pages[0])
for n,(title,page) in sorted(sections.items()):raw.add_outline_item(f'{n}. {title}',page+(1 if page>=1 else 0))
raw.add_metadata({'/Title':'FORGE - an AI integrated E Commerce System','/Author':'Mark Reeve P. Consul; Sarigumba Generry; Blessy May Manila Aldaya; Melca Alegado; Aron Jef Tugahan','/Subject':'OOP course project documentation with UML screenshots and testing evidence','/Creator':'FORGE documentation tools'})
raw.write(str(OUT/'FORGE-PROJECT-REPORT.pdf'))
uml=PdfWriter();intro=text_pdf('uml-cover','# FORGE UML Class Diagram\n\n**Project:** FORGE - an AI integrated E Commerce System\n\n15 sheets covering the current application.\n\n**Verified source snapshot:** 24 October 2026\n\nThe first 14 sheets contain every top-level Java type; the last sheet explains dependencies across layers. The companion class reference covers all 165 named types and their declared members.\n\nNotation: + public, # protected, - private, ~ package access. Record fields are private final; Java generates public component accessors. Diagrams show selected API excerpts. Full member signatures, overloads, source locations and source hashes are included in docs/uml.\n\nEach sheet is landscape A3 for readable labels and can also be opened as SVG.\n\n**Group:** Mark Reeve P. Consul; Sarigumba Generry; Blessy May Manila Aldaya; Melca Alegado; Aron Jef Tugahan.\n\n**Course and section:** BSIT - 2 Section 06\n\n**Instructor:** Ms. Mary Clarence Bababtid')
uml.append(str(intro))
for i,(item,path) in enumerate(zip(index,diagpaths),1):uml.append(str(path));uml.add_outline_item(item['title'],i)
for i,page in enumerate(uml.pages):
 w,h=float(page.mediabox.width),float(page.mediabox.height);b=io.BytesIO();c=canvas.Canvas(b,pagesize=(w,h));c.setFont('Body',8);c.drawString(35,22,'FORGE  |  UML source snapshot 24 October 2026');c.drawRightString(w-35,22,f'{i+1} / {len(uml.pages)}');c.save();page.merge_page(PdfReader(b).pages[0])
uml.add_metadata({'/Title':'FORGE - an AI integrated E Commerce System - UML Class Diagram','/Author':'FORGE project group'});uml.write(str(OUT/'FORGE-UML.pdf'))
print(f'Built report with {len(raw.pages)} pages and UML PDF with {len(uml.pages)} pages.')
(WORK/'report-layout.json').write_text(json.dumps({'reportPages':len(raw.pages),'umlPages':len(uml.pages),'sections':sections},indent=2)+'\n')
