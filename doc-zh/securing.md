# 给 Okapi 的安装(实例)提供安全保障

这是关于如何结合使用FOLIO aut来给Okapi的安装实例提供安全保障的快速指南。希望这一过程将在某一时刻实现自动化。

## 运行示例

下面将演示`curl`命令。首先，确保Okapi和所需的模块已经准备完毕。然后将它们复制粘贴到另一个命令行控制台上。

如果你已熟悉这块那么可以跳过。正如Okapi指南所示，这一行可以用来提取样本数据到/tmp中，并运行curl命令:

```
perl -n -e 'print if /^```script/../^```$/;' okapi/doc/securing.md | sed '/```/d' | sh -x
```

所有的shell命令都假定位于顶级目录中，如:`~/folio`。假设所有模块都位于它的下面，每个模块都位于自己的目录中。

## Okapi

### 获取Okapi

这个脚本假设您有一个新的Okapi实例正在运行，其中没有安装任何东西。如果没有，您可以通过运行以下命令来启动一个:

```
git clone https://github.com/folio-org/okapi
cd okapi
#git checkout "v2.3.1"  # Or "master" if you want the latest, or any other tag
mvn clean install
cd ..
```

构建需要一点时间。（如果在mvn命令行中添加' -DskipTests '，速度会更快）

确认日志最后一样显然如下内容:

```
[INFO] BUILD SUCCESS
```

### 初始化数据库

你需要一个数据库来存放所有的东西，所以你应该从一个完全干净的数据库开始。

创建数据库用户:

```
  sudo -u postgres -i
   createuser -P -s okapi   # When it asks for a password, enter okapi25
   createdb -O okapi okapi
```

注意 `-s` 赋予用户超级用户权限，这在租户接口调用中是必需的。如果你遵循Okapi指南，那么你可能已经创建了Okapi用户，但它并没有超级用户权限。你可以通过如下命令赋予用户超级用户权限：

```
sudo -u postgres psql -c "ALTER USER okapi  WITH SUPERUSER"
```

具体详见 https://github.com/folio-org/folio-ansible/blob/master/roles/postgresql/tasks/main.yml

<!--
Marc's example:
CREATE ROLE module_admin_user PASSWORD ‘somepassword’ SUPERUSER CREATEDB INHERIT LOGIN;
-->

详见
[存储](https://github.com/folio-org/okapi/blob/master/doc/guide.md#storage)
有关设置数据库的详细信息，请参阅Okapi指南. 之后运行:

```
java -Dstorage=postgres -jar okapi/okapi-core/target/okapi-core-fat.jar initdatabase
```

### 运行Okapi

```
java -Dstorage=postgres -jar okapi/okapi-core/target/okapi-core-fat.jar dev
```

最后你应该让Okapi在控制台窗口中运行并监听9130端口。可以通过列出租户来确认其是否运行:

```script
curl -w '\n' -D - http://localhost:9130/_/proxy/tenants
```

```
HTTP/1.1 200 OK
Content-Type: application/json
X-Okapi-Trace: GET okapi-2.16.1-SNAPSHOT /_/proxy/tenants : 200 11141us
Content-Length: 105

[ {
  "id" : "supertenant",
  "name" : "supertenant",
  "description" : "Okapi built-in super tenant"
} ]
```

注意curl命令行选项:

* `-w '\n'` 使curl在操作后输出新行
* `-D -` 将curl响应头输出到控制台


## 必要模块

脚本需要如下模块::
* mod-permissions
* mod-users
* mod-login
* mod-authtoken

如下命令可以获取并编译模块。我们使用master分支的版本，但是要注意，它随时可能会变化。你可以添加一个`git checkout`以获得一个已知的好版本。

```
git clone --recursive https://github.com/folio-org/mod-permissions
cd mod-permissions
git checkout master # v5.2.5 is okay
mvn clean install
cd ..

git clone --recursive https://github.com/folio-org/mod-users
cd mod-users
git checkout master # v15.1.0 is okay
mvn clean install
cd ..

git clone --recursive https://github.com/folio-org/mod-login
cd mod-login
git checkout master # v4.0.1 is okay
mvn clean install
cd ..

git clone https://github.com/folio-org/mod-authtoken
cd mod-authtoken
git checkout master # v1.4.1 is okay
mvn clean install
cd ..
```

## 操作顺序

基本上我们只需要声明、部署和启用上面声明的模块，并将一些数据加载到其中。

声明和部署模块的顺序可以是任意的，但我们必须注意到模块启用的顺序。具体来说，我们可能直到最后才能启用mod-authtoken，此时，所有其他模块都已准备就绪并完成数据加载。因为od-authtoken会把我们锁在系统之外不让我们完成这个过程。

### 声明模块

在构建过程中会生成模块的描述文件。无论模块来自哪里，我们只需要将其描述文件POST给Okapi。

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-permissions/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-users/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-login/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-authtoken/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules
```

您可以验证我们已正确声明所有四个模块:

```script
curl -w '\n' -D -  http://localhost:9130/_/proxy/modules
```

这里会显示5个模块，4个是上述声明的模块，一个是Okapi的内部模块。This should list five modules, the 4 declared above, and Okapi's internal module.

### 设置数据库环境

所有的模块需要知道怎么和数据库进行通信。最简单的方式是通过向Okapi以POST方式发送要设置的环境变量。

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_HOST", "value":"localhost"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_PORT", "value":"5432"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_USERNAME", "value":"okapi"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_PASSWORD", "value":"okapi25"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_DATABASE", "value":"okapi"}' \
  http://localhost:9130/_/env
```

### 部署模块

现在我们需要部署模块。这里我们只是使用每个模块为其开发目的提供的默认部署描述文件，因此需要进行一些调整。

#### mod-permissions

```script
cat mod-permissions/target/DeploymentDescriptor.json \
  | sed 's/..\///' | sed 's/embed_postgres=true//' > /tmp/deploy-permissions.json

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-permissions.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-users

```script
cat mod-users/target/DeploymentDescriptor.json \
  | sed 's/..\///' | sed 's/embed_postgres=true//' > /tmp/deploy-users.json

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-users.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-login

```script
cat mod-login/target/DeploymentDescriptor.json \
  | sed 's/..\///' | sed 's/embed_postgres=true//' > /tmp/deploy-login.json

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-login.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-authtoken

```script
cat mod-authtoken/target/DeploymentDescriptor.json \
  | sed 's/..\///' | sed 's/embed_postgres=true//' > /tmp/deploy-authtoken.json

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-authtoken.json \
  http://localhost:9130/_/discovery/modules
```

你可以通过如下命令查看4个模块的部署情况:

```script
curl -w '\n' -D - http://localhost:9130/_/discovery/modules
```

### 启用模块和加载数据

为超级租户启用模块。我们不需要在这里指定版本号，Okapi将选择我们声明的最新(且唯一)版本。

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
   -d'{"id":"mod-permissions"}' \
   http://localhost:9130/_/proxy/tenants/supertenant/modules
```

#### Create our superuser in the perms module

```script
cat > /tmp/permuser.json <<END
{ "userId":"99999999-9999-9999-9999-999999999999",
  "permissions":[ "okapi.all" ] }
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/permuser.json \
   http://localhost:9130/perms/users
```

#### Enable mod-users

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-users"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

在mod-users中创建我们的超级用户

```script
cat > /tmp/superuser.json <<END
{ "id":"99999999-9999-9999-9999-999999999999",
  "username":"superuser",
  "active":"true",
  "personal": {
     "lastName": "Superuser",
     "firstName": "Super"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/superuser.json \
   http://localhost:9130/users
```

#### mod-login

启用mod-login:

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-login"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

创建一个登录用户:

```script
cat >/tmp/loginuser.json << END
{ "username":"superuser",
  "password":"secretpassword" }
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/loginuser.json \
   http://localhost:9130/authn/credentials
```


#### mod-authtoken

当我们启用mod-authtoken时，系统最终被锁定。

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-authtoken"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

#### 登录

我们可以复用用于创建登录用户的凭据。我们需要将消息头保存到/tmp文件夹中以便我们提取auth的令牌。

```script
curl -w '\n' -D /tmp/loginheaders -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/loginuser.json \
   http://localhost:9130/authn/login

cat /tmp/loginheaders | grep -i x-okapi-token | sed 's/ //g' > /tmp/token
echo "Got token " `cat /tmp/token`
```

#### 确认我们需要一个令牌

试图创建一个没有令牌的租户，这是应该会失败。

Try to create a new tenant, without the token. Should fail.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"failure","name":"not permitted"}' \
   http://localhost:9130/_/proxy/tenants
```

#### 确认令牌有效

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -H `cat /tmp/token` \
  -d'{"id":"testtenant","name":"Simple test"}' \
   http://localhost:9130/_/proxy/tenants
```

#### 确认我们可以显示所有的租户

应该允许任何用户在没有令牌的情况下执行大多数的只读操作。

```script
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant:supertenant" \
   http://localhost:9130/_/proxy/tenants
```

这里应该会列出两个租户。

### 清除

如果你需要清除你已创建的东西，你需要做如下两件事情:删除我们创建的所有角色和模式；删除并重新创建整个数据库。行类似`sudo -u postgres psql okapi`的命令，并将以下命令粘贴到其中:

```
DROP SCHEMA supertenant_mod_login CASCADE;
DROP ROLE supertenant_mod_login;

DROP SCHEMA supertenant_mod_users CASCADE;
DROP ROLE supertenant_mod_users;

DROP SCHEMA supertenant_mod_permissions CASCADE;
DROP ROLE supertenant_mod_permissions;
\q
```

运行如下两个命令:

```
sudo -u postgres dropdb okapi
sudo -u postgres createdb -O okapi okapi
```


