import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.lang.model.element.Modifier;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
class SourceInventory {
 static List<Map<String,Object>> types=new ArrayList<>();
 static Map<String,Object> obj(Object...xs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<xs.length;i+=2)r.put((String)xs[i],xs[i+1]);return r;}
 static String mods(Set<Modifier> flags){return String.join(" ",flags.stream().map(Object::toString).toList());}
 static void read(ClassTree c,CompilationUnitTree unit,String parent,Trees trees,Path root){
  String name=c.getSimpleName().toString();if(name.isEmpty())return;String pkg=unit.getPackageName().toString();String local=parent.isEmpty()?name:parent+"."+name;
  var fields=new ArrayList<Map<String,Object>>();var methods=new ArrayList<Map<String,Object>>();
  for(Tree member:c.getMembers()){
   if(member instanceof VariableTree v)fields.add(obj("name",v.getName().toString(),"type",v.getType()==null?name:v.getType().toString(),"modifiers",mods(v.getModifiers().getFlags()),"enumConstant",v.getType()!=null&&v.getType().toString().equals(name)&&v.getInitializer() instanceof NewClassTree));
   if(member instanceof MethodTree m){var parameters=m.getParameters().stream().map(v->obj("name",v.getName().toString(),"type",v.getType().toString())).toList();methods.add(obj("name",m.getName().toString(),"returnType",m.getReturnType()==null?"":m.getReturnType().toString(),"parameters",parameters,"modifiers",mods(m.getModifiers().getFlags()),"throws",m.getThrows().stream().map(Object::toString).toList()));}
  }
  long pos=trees.getSourcePositions().getStartPosition(unit,c);String file=root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
  types.add(obj("name",name,"localName",local,"package",pkg,"qualifiedName",pkg+"."+local,"kind",c.getKind().toString(),"modifiers",mods(c.getModifiers().getFlags()),"extends",c.getExtendsClause()==null?"":c.getExtendsClause().toString(),"implements",c.getImplementsClause().stream().map(Object::toString).toList(),"fields",fields,"methods",methods,"source",file,"line",unit.getLineMap().getLineNumber(pos),"nested",!parent.isEmpty()));
  for(Tree member:c.getMembers())if(member instanceof ClassTree nested)read(nested,unit,local,trees,root);
 }
 public static void main(String[] a)throws Exception{
  Path root=Path.of(a[0]).toAbsolutePath().normalize();var paths=Files.walk(root.resolve("src/main/java")).filter(p->p.toString().endsWith(".java")).sorted().toList();var compiler=ToolProvider.getSystemJavaCompiler();var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8);var task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjectsFromPaths(paths));var trees=Trees.instance(task);for(var unit:task.parse())for(Tree declaration:unit.getTypeDecls())if(declaration instanceof ClassTree c)read(c,unit,"",trees,root);Files.writeString(Path.of(a[1]),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(types),StandardCharsets.UTF_8);System.out.println("Parsed "+paths.size()+" source files and "+types.size()+" declared named types without executing application code.");System.exit(0);
 }
}
