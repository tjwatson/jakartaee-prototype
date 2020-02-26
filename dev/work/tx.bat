set TX_DEPS=C:\dev\jakarta-repo-pub\jakartaee-prototype\dev\transformer\build\archives
set TX_LIBS=C:\dev\jakarta-repo-pub\jakartaee-prototype\dev\transformer\build\libs

set TX_DEP_LIBS=%TX_DEPS%\biz.aQute.bndlib-4.3.1.jar;%TX_DEPS%\commons-cli-1.4.jar;%TX_DEPS%\slf4j-api-1.7.29.jar;%TX_DEPS%\slf4j-simple-1.7.29.jar
set TX_LIB=%TX_LIBS%\transformer.jar

set TX_CP=%TX_DEP_LIBS%;%TX_LIB%
set TX_CLASS=com.ibm.ws.jakarta.transformer.JakartaTransformer

set TX_SELECTIONS=servlet4.selections

set TX_CMD=java -cp %TX_CP% %TX_CLASS%

%TX_CMD% %*

REM del servlet.4.output.jar
REM tx servlet.4.jar servlet.4.output.jar -o -ts servlet4.selections