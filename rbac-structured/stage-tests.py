"""Stage compiled tests outside the repository for fast Linux-container JDBC execution.
The host Maven reactor remains the compiler; this avoids slow Windows Docker port forwarding.
"""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
root=Path('D:/m3-structured-run')
root.mkdir(exist_ok=True)
(root/'lib').mkdir(exist_ok=True)
(root/'results').mkdir(exist_ok=True)
xml=ET.parse('rbac-structured/target/surefire-reports/TEST-com.xmu.rbac.structured.MappingSafetyTest.xml')
classpath=next(p.attrib['value'] for p in xml.findall('.//property') if p.attrib['name']=='java.class.path')
for item in classpath.split(';'):
    path=Path(item)
    if path.is_file():
        shutil.copy2(path,root/'lib'/path.name)
for source,target in [('rbac-structured/target/classes','classes'),('rbac-structured/target/test-classes','test-classes'),('rbac-contract/target/classes','contract-classes')]:
    target=root/target
    if target.exists(): shutil.rmtree(target)
    shutil.copytree(source,target)
repository=Path('C:/Users/vintces/.m2/repository')
for relative in ['org/junit/platform/junit-platform-launcher/1.10.5/junit-platform-launcher-1.10.5.jar','org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar']:
    path=repository/relative
    shutil.copy2(path,root/'lib'/path.name)
(root/'RunTests.java').write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
public class RunTests {
 public static void main(String[] args) throws Exception {
  LauncherDiscoveryRequestBuilder builder=LauncherDiscoveryRequestBuilder.request();
  for(String name:args) {
   if(name.contains("#")) {String[] parts=name.split("#",2);builder.selectors(DiscoverySelectors.selectMethod(parts[0],parts[1]));}
   else builder.selectors(DiscoverySelectors.selectClass(name));
  }
  org.junit.platform.launcher.Launcher launcher=LauncherFactory.create();SummaryGeneratingListener listener=new SummaryGeneratingListener();
  launcher.registerTestExecutionListeners(listener);launcher.execute(builder.build());
  java.io.PrintWriter writer=new java.io.PrintWriter(System.out,true);listener.getSummary().printTo(writer);listener.getSummary().printFailuresTo(writer);
  System.exit(listener.getSummary().getTotalFailureCount()>0?1:0);
 }
}''',encoding='utf-8')
print(root)
