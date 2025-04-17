<div id="user-content-toc" align="center"><ul><summary><h1 align="center">Java Library for Oracle Service Bus</h1></summary></ul></div>

## Build instructions
<p align="justify">The sources can be compiled with any Java IDE with Ant support but you need to prepare the necessary dependencies for WebLogic and Oracle Service Bus libraries. You only need to modify "javaHomeDir" and "weblogicDir" in "Build.xml" file to suit your environment. The file supports multiple terget already prepared for WebLogic 12.1.3, 12.2.1 and 14.1.2 on a Windows operating system. Here is an excerpt of the section that needs to be customized.</p>

```xml
    <switch value="${targetConfig}">
      <case value="12.1.3">
        ...
        <property name="javaHomeDir" value="C:/Programmi/Java/jdk1.7"/>
        <property name="weblogicDir" value="C:/Oracle/Middleware/12.1.3"/>   
        ...
      </case>
      <case value="12.2.1">
        ...
        <property name="javaHomeDir" value="C:/Programmi/Java/jdk1.8"/>
        <property name="weblogicDir" value="C:/Oracle/Middleware/12.2.1"/>    
        ...
      </case>        
      <case value="14.1.2">
        ...
        <property name="javaHomeDir" value="C:/Programmi/Java/jdk17"/>
        <property name="weblogicDir" value="C:/Oracle/Middleware/14.1.2"/>    
        ...
      </case>        
      <default>
        <fail message="Unsupported target: ${targetConfig}"/>
      </default>
    </switch>    
```
<p align="justify">The repository contains three projects already prepared for JDeveloper 12.1.3, 12.2.1.4 and 14.1.2 installation on Windows operating system. You only need to adjust the paths of the external libraries which must point to the installation path of the respective JDeveloper. You could install JDeveloper with respective versions of Oracle SOA Suite Quick Start for Developers (see references). Ant compilation can be triggered from JDeveloper by right-clicking on the "Build.xml" file and selecting the "all" target or from the command line by running the "Build-xxx.cmd" Windows batch. Note that cross-compilation is fully supported, meaning that you can compile the provider for a different version target than JDeveloper, provided that at least the dependency libraries are accessible and configured correctly in the Ant build targets.</p>

At the end of the compilation a single "fat" (or "merged") jar archive is produced. You have to copy it into the folder ```<DOMAIN_HOME>/config/lib``` to make the classes available in WebLogic for various purposes such as implementing Security Providers or implementing OSB extensions using Java Callouts.</p>

## Credits
- **JSON-java** (https://github.com/stleary/JSON-java)<br/>
- **Mozilla Rhino** (https://github.com/mozilla/rhino)
- **Apache HttpClient** (https://hc.apache.org/httpcomponents-client-4.5.x/index.html)<br/>
- **Nimbus JOSE + JWT** (https://connect2id.com/products/nimbus-jose-jwt)<br/>
