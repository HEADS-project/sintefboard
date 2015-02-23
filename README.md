This javacode can be added as part of a Kevoree javanode.

To run the code you need to have a Cypress dev board with correct FW code running.
The kev.script has to be edited to idenitfy the specific serial port to be used.

The javacode can be compiled using maven:
   mvn clean install
   
The Kevoree javanode can be started using maven:
   mvn kev:run
   
   
