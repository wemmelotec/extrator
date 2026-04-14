# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.13/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.13/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.13/reference/web/servlet.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/3.5.13/reference/using/devtools.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven warnings in Eclipse/STS (Java 25+)

If you run Maven goals (for example `clean`) from Eclipse/STS and see warnings about:

* `java.lang.System::load` (Jansi)
* `sun.misc.Unsafe` (Guice)

this comes from the Maven runtime embedded in Eclipse/STS, not from this application code.

Project-side mitigation already configured:

* `.mvn/jvm.config` enables:
	* `--enable-native-access=ALL-UNNAMED`
	* `--sun-misc-unsafe-memory-access=allow`

Recommended Eclipse/STS fix (for embedded Maven runtime):

1. Edit your `STS.ini` (or `eclipse.ini`) and add, after `-vmargs`:
	 * `--enable-native-access=ALL-UNNAMED`
	 * `--sun-misc-unsafe-memory-access=allow`
2. Restart Eclipse/STS.

Alternative:

* Run Eclipse/STS with JDK 21 (LTS), where these warnings are not emitted in the same way.

Runtime warning during OCR requests:

* Use the provided [ExtratorApplication.launch](ExtratorApplication.launch) in Eclipse/STS, or copy the same VM argument into your Run Configuration:
	* `--enable-native-access=ALL-UNNAMED`

This is the flag required by JavaCPP/OpenCV/Javacv when the OCR pipeline loads native libraries at request time.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

