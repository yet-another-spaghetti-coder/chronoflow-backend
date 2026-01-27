1. Open Docker. Run "docker-compose up -d" This will create mysql, nacos, redis, mongo.
So please make sure these ports is available: 3306, 6379, 8848, 9848, 9849, 27017
2. Go to http://localhost:8848/nacos. Then find namespace and create a new one.!
[img.png](img.png)
3. Download .env file. This file includes the environment configs.
For Win users, just put this file into project root menu. And config each service in IDEA to read this file.