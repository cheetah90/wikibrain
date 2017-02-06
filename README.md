# Setup WikiBrain - Atlasify
### 1. Environment
Maven, Java 1.7+, PostGIS

### 2. Clone the codebase
Clone the master branch of this repo
`git clone git@github.com:cheetah90/wikibrain.git`

### 3. Setup Java Options
`JAVA_OPTS="-d64 -Xmx16000M -server"` make sure you have a host with RAM > 16G. Set the Xmx higher if you have more RAM.

### 4. Configure database
Edit `wikibrain-core/src/main/resources/reference.conf`
```
dao:dataSource:default: psql
dao:dataSource:psql: put in the username and password for postgres
spatial:dao:dataSource:default: postgis
spatial:dao:dataSource:postgis: put in the username and password for postgres
```
### 5. Compile WikiBrain
At the project root (/wikibrain) run `mvn -f wikibrain-utils/pom.xml clean compile exec:java -Dexec.mainClass=org.wikibrain.utils.ResourceInstaller`

### 6. Tune postgres
According to https://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server
```
listen_addresses = '*'
max_connections = 500         # Must be at least 300
shared_buffers = 48GB         # Should be 1/4 of system memory
effective_cache_size = 96GB   # Should be 1/2 of system memory
fsync = off                 
synchronous_commit = off    
checkpoint_segments = 256
checkpoint_completion_target = 0.9
autovacuum = off
```
### 7. Start Data Ingestion
`./wb-java.sh org.wikibrain.Loader -l en -s wikidata -s spatial`
#### Debug: 
If SSL Certificate error occurs, you need to add the certificate from dump.wikimedia.org to the java keystore

To download the cert `echo -n | openssl s_client -connect dumps.wikimedia.org:443 | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > ~/dumpswikimedia.cert`

To add it to java cacert, first locate the cercate  `keytool -keystore cacerts -importcert -alias equifaxsecureca -file Equifax_Secure_Certificate_Authority.cer
`

### 9. Configure the URL
Edit `atlasify/src/main/java/org/wikibrain/atlasify/AtlasifyLauncher.java`. set externalURL and portNo according to the information of the host

### 10. Host the front end
Host https://github.com/tobyli/Atlasify with your favorite http server (e.g. Apache)

### 11. Configure the front end
change the baseURL and featureArticleURL in atlasify.js based on the host info

### 12. Start Server
run `./wb-java.sh org.wikibrain.atlasify.AtlasifyLauncher`

### 13. Test
Open index.html to try if everything works. Note: run a query first -- and then the back-end will start loading. Wait till the loading finishes to try another query.
