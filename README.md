## FScript

<p align="center"><img src="https://github.com/wholder/FScript/blob/master/images/FScript%20Screenshot.png"></p>

FScript provides a simple GUI interface for editing and running FScript code using a Java-based intepreter.  This code is derived from the original FScript by Joachim Van der Auwera.  For documentation on the language, see [http://fscript.sourceforge.net/fscript/index.html](http://fscript.sourceforge.net/fscript/index.html) but here is a quick rundown of FScript's features:

 - Three supported data types (string, integer, double)
 - Conditional execution ('**`if`**' '**`elseif`**' and '**`else`**' statements)
 - Loops ('**`while`**')
 - Functions (including recursive functions)
 - Local & global variable scope
 - The usual range of logic and math operators: **`+ - * / % == != >= <= && || ! ( )`** 
   - Boolean operators **`&& ||`** only work with integer operands
   - Math operators **`+ - * / %`** are valid for all numeric types
   - The equality & inequality operators **`== !=`** are valid for all types (yields an integer 1 or 0 as a result)
   - The **`+`** operator is also valid for string concatenation
   - The comparison **`>= <=`** operators can only compare operands of the same type (yields an integer 1 or 0 as a result)
 - Simple to extend either by sub-classing or 'plug-in' like extensions
 - Note: Carriage returns are significant in FScript, so **`if a >= 100 a = 100 endif`** on one line will not work.

My goal is to eventually integrate this version of FScript into some of my other programs, such as [LaserCut](https://github.com/wholder/LaserCut) for use is as an internal scripting engine, but I decided to publish it here as a demonstration of how other might use it for the same purpose.

### Requirements
Java 8 JDK, or later must be installed in order to compile the code.  There is also a [**Runnable JAR file**](https://github.com/wholder/FScript/blob/master/out/artifacts/FScript_jar) included in the checked in code that you can download.   On a Mac, just double click the **`FScript.jar`** file and it should startOn a Mac.  However, you'll probably have to right click and select "Open" the  first time you run FScript due to new Mac OS X security checks.  You should also be able to run the JAR file on Windows or Linux systems, but you'll need to have a Java 8 JRE, or later installed and follow the appropriate process for each needed to run an executable JAR file.

## Credits
FScript uses the following Java code to perform some of its functions, or build this project:
- [FScript](http://fscript.sourceforge.net) this code is derived from FScript by Joachim Van der Auwera (murlen)
- [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/) (my favorite development environment for Java coding. Thanks JetBrains!)
