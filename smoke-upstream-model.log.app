
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::               (v3.3.13)

2026-10-03T23:20:09.499+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.chobit.knot.gateway.AdminApplication   : Starting AdminApplication v0.0.1-SNAPSHOT using Java 25.0.3 with PID 40812 (D:\MyDevelop\JDevelop\workspace\knot\knot-server\knot-admin\target\knot-admin-0.0.1-SNAPSHOT.jar started by robin in D:\MyDevelop\JDevelop\workspace\knot)
2026-10-03T23:20:09.500+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.chobit.knot.gateway.AdminApplication   : No active profile set, falling back to 1 default profile: "default"
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::loadLibrary has been called by org.apache.tomcat.jni.Library in an unnamed module (jar:nested:/D:/MyDevelop/JDevelop/workspace/knot/knot-server/knot-admin/target/knot-admin-0.0.1-SNAPSHOT.jar/!BOOT-INF/lib/tomcat-embed-core-10.1.60.jar!/)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

2026-10-03T23:20:10.878+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 18080 (http)
2026-10-03T23:20:10.895+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-10-03T23:20:10.896+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.60]
2026-10-03T23:20:10.932+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2026-10-03T23:20:10.932+08:00  INFO 40812 --- [knot-ai-gateway] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 1376 ms
2026-10-03T23:20:12.091+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.impl.StdSchedulerFactory      : Using default implementation for ThreadExecutor
2026-10-03T23:20:12.104+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.core.SchedulerSignalerImpl    : Initialized Scheduler Signaller of type: class org.quartz.core.SchedulerSignalerImpl
2026-10-03T23:20:12.104+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.core.QuartzScheduler          : Quartz Scheduler v.2.3.2 created.
2026-10-03T23:20:12.105+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.simpl.RAMJobStore             : RAMJobStore initialized.
2026-10-03T23:20:12.105+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.core.QuartzScheduler          : Scheduler meta-data: Quartz Scheduler (v2.3.2) 'quartzScheduler' with instanceId 'NON_CLUSTERED'
  Scheduler class: 'org.quartz.core.QuartzScheduler' - running locally.
  NOT STARTED.
  Currently in standby mode.
  Number of jobs executed: 0
  Using thread pool 'org.quartz.simpl.SimpleThreadPool' - with 10 threads.
  Using job-store 'org.quartz.simpl.RAMJobStore' - which does not support persistence. and is not clustered.

2026-10-03T23:20:12.105+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.impl.StdSchedulerFactory      : Quartz scheduler 'quartzScheduler' initialized from an externally provided properties instance.
2026-10-03T23:20:12.105+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.impl.StdSchedulerFactory      : Quartz scheduler version: 2.3.2
2026-10-03T23:20:12.105+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.core.QuartzScheduler          : JobFactory set to: org.springframework.scheduling.quartz.SpringBeanJobFactory@4809c771
2026-10-03T23:20:12.884+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 18080 (http) with context path '/'
2026-10-03T23:20:12.885+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.s.s.quartz.SchedulerFactoryBean        : Starting Quartz Scheduler now
2026-10-03T23:20:12.886+08:00  INFO 40812 --- [knot-ai-gateway] [           main] org.quartz.core.QuartzScheduler          : Scheduler quartzScheduler_$_NON_CLUSTERED started.
2026-10-03T23:20:12.897+08:00  INFO 40812 --- [knot-ai-gateway] [           main] o.chobit.knot.gateway.AdminApplication   : Started AdminApplication in 3.819 seconds (process running for 4.224)
2026-10-03T23:20:12.917+08:00  INFO 40812 --- [knot-ai-gateway] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-10-03T23:20:13.037+08:00  INFO 40812 --- [knot-ai-gateway] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@13f182b9
2026-10-03T23:20:13.038+08:00  INFO 40812 --- [knot-ai-gateway] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-10-03T23:20:16.326+08:00  INFO 40812 --- [knot-ai-gateway] [io-18080-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-10-03T23:20:16.326+08:00  INFO 40812 --- [knot-ai-gateway] [io-18080-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-10-03T23:20:16.327+08:00  INFO 40812 --- [knot-ai-gateway] [io-18080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 1 ms
2026-10-03T23:20:16.486+08:00  WARN 40812 --- [knot-ai-gateway] [io-18080-exec-1] org.chobit.knot.gateway.auth.JwtUtil     : [安全] JWT 签名密钥使用开发默认值，生产环境必须通过环境变量 KNOT_JWT_SECRET 或系统属性 knot.jwt.secret 注入
2026-10-03T23:20:18.028+08:00  WARN 40812 --- [knot-ai-gateway] [io-18080-exec-6] o.c.knot.gateway.GlobalExceptionHandler  : Business exception: code=GW-SYSTEM-400, message=请填写上游模型
