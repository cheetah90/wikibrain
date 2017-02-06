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
