# Okapi的安全模型

<!-- ./md2toc -l 2 -h 3 security.md -->
* [介绍](#介绍)
* [概述](#概述)
* [认证](#认证)
* [授权](#授权)
* [数据流示例](#数据流示例)
    * [简单的请求：以日期为例](#简单的请求-以日期为例)
    * [更复杂的请求: 今日消息(MOTD)](#更复杂的请求-今日消息)
    * [登录](#登录)
* [开放式问题和技术细节](#开放式问题和技术细节)
* [Okapi的处理](#okapi的处理)
* [auth的处理](#auth的处理)

## 介绍

Okapi的安全模型由认证和授权两部分组成。认证用以确保对方是确切的用户，授权用以检查是否运行执行该操作。我们有一个与授权相关的系统用以管理大量的权限位。Okapi并不关心这些是在单独的模块中处理还是作为授权模块的一部分。Okapi的权限只有两个来源：不是用授权就是模块授权。

就Okapi中所看到的权限而言，他们只是诸如"parton.read"或者"parton.read.sensitive"之类的简单的字符串。这些字符串对于Okapi而言并没有什么意义，但是我们必须有一些命名准则。一方面，模块应该在非常细粒度的级别上定义它们的权限，允许细粒度控制。但是另一方面，管理这些权限应该非常简单。这可以通过权限模块将用户角色扩展为分组的权限，并将其扩展为细粒度的权限来实现。例如：一个'sysadmin'角色可以扩展为一个包含'patron.admi''的列表，它也可以扩展为包含'patron.read'，'patron.update'和'patron.create'的列表。所有这些都应该在权限模块中完成。

Okapi的源码树中包括一个名为`okapi-test-auth`的小模块，它以虚拟方式来实现认证和授权，用以测试Okapi本身。它并不是一个真正的安全模块。事实上认证和授权模块可能由多个单独的模块来实现。

## 概述

除去所有的细节，以下这些是一个用户想要使用Okapi是或多或少会遇到的：

* 用户做的第一件事是将浏览器指向登录屏幕。
* 输入他们的账号信息。
* 授权模块校验这些信息。
* 给出一个令牌并将其返回给用户。
* 用户将此令牌添加到Okapi中所有的请求。
* Okapi调用授权模块来验证我们是是一个有效用户，并且具有发出请求的权限。
* 模块可以通过Okapi进一步调用其它模块。同样，调用过程中要再一次传递令牌，同时Okapi将令牌传递给授权模块来验证它。如果模块有特殊权限，Okapi可以传递修改过的令牌。

## 认证

显然，我们需要不同的认证不同的认证机制，从SAML、Oauth、Ldap到用户名/密码，再到是IP认证，甚至是一些为表明不同身份的伪认证。

通常应该至少为每个租户启用一个认证模块。用户的第一个请求应该是 /authn/login，它被指向到正确的验证模块。它将获得一些诸如租户、用户名/密码之类的参数。它将验证这些参数，并于后端进行会话。当满足一切条件，它将通过调用授权模块获得JWT令牌，这些令牌则是授权模块真正关心的。在任何情况下都会向客户端返回令牌。令牌始终包含用户ID及用户所属的租户，也可能包含其他许多信息。因为它是加密的所以，在没有检测的情况下是不能对它做任何修改。

模块还可以从后端获得一些权限数据，并且向模块（或权限，如果是单独模块）发出更新请求。

客户端将在所有的请求中传递这个令牌，并且认证模块每次都会验证它。


## 授权

这是Okapi参与较多的地方，也是比较有技术含量的一节。

当一个来自Okapi的请求（包括 /authn/login 或其他对任何人开放的请求）,Okapi会查看租户并找出其将调用的模块的管道。这应该包括（或者接近）开头的授权模块。

同时，Okapi会从模块描的描述文件中查看所有相关模块的权限位。这里有三种不同的权限：

* permissionsRequired. 这些对于调用模块是非常重要的
* permissionsDesired. 这并不是必要的，但是如果存在，模块可能会执行一些额外的操作（例如，显示关于用户的敏感数据）
* modulePermissions. 与上述两者不同，这些是授予模块的权限，模块在进一步调用其他模块时可以使用这些权限。

如之前所提到的，Okapi不会检查任何权限。它只收集`permissionsRequired` 和`permissionsDesired`，并将他们传递给授权模块进行检查。它也为管道中的每个模块收集`modulePermissions`,并传递给授权模块。

认证模块首先会校验我们的令牌是否合法，其签名与内容是否匹配。随后，从令牌中提取用户和租户ID，并查询用户相应的权限。它还会检查令牌中是否包含模块权限，如果有，则这些模块权限会添加到用户权限列表中。接下来，它会检查所有的'permissionsRequired'，是否确实存在权限列表中，如果缺少任何一个，它就会使得整个请求失败。授权模块还将遍历`permissionsDesired`权限，并检查其中哪些存在。它在一个特殊的消息头中报告这些内容，这样后续模块可以查看它们，并依此决定是否修改它们的行为。

最后，授权模块会查看其收到的`modulePermissions`。对于其中列出的每个模块会创建一个包含于原始模块相同内容的新的JWT，并将权限授予给该模块。它生成JWT并返在名为X-Okapi_module-Tokens的消息头将其全部返回给Okapi。如果原始令牌包括一些模块权限的话。授权模块还会生成一个没有这些权限的新令牌用于所有没有获得特殊权限的模块。这或多或少与用户原始令牌相似。

Okapi会检查它是否收到任何模块令牌，并将这些令牌存储在管道中，这样它就可以将诸如一个特殊令牌传递给那些具有特殊权限的模块。其余的模块将获得一个干净的令牌。

以下模块既不知道也不关心它们是否从UI接收到原始令牌，也不关心为它们专门设计的令牌。他们只是在任何请求中把它传递给其他模块，Okapi则证这一点。

这里有一些特殊情况，如用户登录时，将完全不包含JWT。授权模块将为Okapi创建一个临时的JWT，以便在此请求期间使用。这个JWT显然不包含任何用户名，因此它不会提供任何用户权限访问的问题。但是它能够调用不需要任何权限的模块，并可以用做在模块期间携带特定模块的权限基础。一旦Okapi处理了请求，就忘记这个JWT。

这会存在安全风险吗？当然不，因为临时的JWT不包含任何权限。调用者可以用选择在发起请求时忽略JWT，但这不会有任何帮助。要么请求不需要任何权限，通常在这种情况下无需JWT就可以调用它。或者请求确实需要某种许可，在大多数情况下会这样。在这种情况下，由于JWT没有授予任何权限，所以请求会被拒绝。

## 数据流示例

这是一些关于数据如何在系统各个部分之间流动的实际例子。我们将从比较简单的案例开始，然后逐步发展到复杂的案例。

### 简单的请求: 以日期为例
假设UI希望展示系统当前日期。我们的用户已经登录到系统中，name我们知道用户的ID("joee")和租户("ourlib")。我们也有一个授权的JWT令牌。它的内部细节在此无关紧要，在这些例子中，假设它们看起来像"xx-joe-ourlib-xx"这样子的格式。UI知道与之通信的Okapi服务器的地址。我们使用`http://folio.org/okapi`这个URL来举例。我们知道已经为这个住户启用了日期模块("cal")，并且有一个服务节点 /date 来返回当前日期。这个服务对任何人都开放。

概述：
* 1.1: UI发出一个请求。
* 1.2： Okapi调用auth模块，用以校验JWT。
* 1.3： Okapi调用cal模块来获取当前日期。

现在具体的细节如下：

1.1：UI发出一个携带额外信息的请求给Okapi：
 * GET `http://folio.org/okapi/date`
 * X-Okapi-Tenant: outlib
 * X-Okapi-Token: xx-joe-ourlib-xx

1.2：Okapi收到请求：
 * 确认我们已知的租户"ourlib"
 * 构建一个 /date 的模块列表，并且为"outlib"启用这些模块
 * 这个列表通常由两个模块组成:首先是"auth"模块，然后是“cal”模块。
 * Okapi注意到请求包含一个X-Okapi-Token后会保存该令牌以备将来所用。
 * Okapi确认这些服务需要哪些权限以及期望哪些权限。在这个例子中并没有。它会创建两个额外的消息头：X-Okapi-Permissions-Required和X-Okapi-Permissions-Desired，并都带有一个空的列表。
 * Okapi确认是否向任何模块授予了任何权限。但事实并非如此。因此它创建了一个不包含任何内容的X-Okapi-Module-Permission。
 * Okapi确认auth模块运行的位置(哪个节点及其端口)。
 * Okapi将这些额外的请求头传递给auth模块：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: [ ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { }
      
1.3：认证模块接收请求：
 * 它会确认我们是否包含消息头X-Okapi-Token。
 * 校验令牌的签名。
 * 从令牌中提取租户ID和用户ID。
 * 检查租户ID是否与消息头X-Okapi-Tenant的中匹配。
 * 由于不需要权限也不需要模块权限，它创建一个空的消息头X-Okapi-Permissions
 * 由于没有涉及特殊模块的权限，它创建了一个空的消息头X-Okapi-Module-Token
 * 返回OK给Okapi并包含一个上述新的消息头：
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { }
     
如果其中任何一个步骤失败的话，那么auth模块将返回一个错误响应。Okapi将停止处理器相应的模块管道，并且把错误响应返回给UI。

1.4: Okapi从auth模块接收OK响应:
 * Okapi会注意到其接收了一个X-Okapi-Module-Tokens的消息头。这意味着已经完成授权，所以，它可以将X-Okapi-Permissions-Required和-Desired的消息头从后续的请求中移除。
 * Okapi发现消息头中没有任何令牌，于是它不会做特殊的事情。
 * Okapi会发现下一个要调用的模块是cal模块。
 * 确认cal模块是否运行。
 * 向cal模块发出带有如下消息头的请求：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions: [ ]
 
1.5：cal模块接收请求：
 * cal模块查找当前日期，并以合适的格式返回。
 * cal模块会向Okapi返回一个Ok响应。这个响应中没有任何特殊的消息头。

1.6：Okapi接收到从cal模块来的Ok响应：
 * Okapi发现这个响应是否来自列表中最后的一个模块。
 * 随后，它将这个响应返回给UI。

1.7：UI在前端显示这个日期。

这个过程看起来比较复杂，但下一个示例应会标明为什么这么多步骤确实是必要的。

注意，这里请求来自UI并没有什么特殊的原因。它可以来自任何发起请求的地方，类似于 cron-like 任务用以在半夜提醒，或者一个查看一个还书机归还信息。但无论如何者必须是一个JWT。

### 更复杂的请求: 今日消息
今日消息(MOTD：Message of the Day) 是一个更复杂的请求。在本例中，我们决定用两种类型的消息，一个用于用户，一个用于员工。员工可以看到员工发送的消息，如果没有员工消息的话可以看到读者信息。通常，用户只可以看到用户的信息。身份不明的普通用户则不允许看到任何东西。

消息保存咋数据库中。motd模块需要想数据库模块发出查询请求。为此，它需要拥有motd模块从数据库中读取数据的权限。

概述：
 * 2.1：UI发出请求。
 * 2.3：auth模块校验JWT并查询用户的权限。
 * 2.7：权限的请求通过Okapi，并发送到perm模块以返回权限。
 * 2.9：auth模块校验用户权限。
 * 2.9：auth模块同时会为motd模块创建一个特殊的JWT切具有相应的权限。
 * 2.11：motd模块用这个特殊的JWT向db模块发送一个请求。
 * 2.12：认证模块校验JWT，并检查相应的权限。
 * 2.15：db模块查询消息。
 * 2.16：消息被一路返回到UI。
 
具体细节如下：

2.1: UI向Okapi发送一个带有额外消息头的请求:
 * GET `http://folio.org/okapi/motd`
 * X-Okapi-Tenant: ourlib
 * X-Okapi-Token: xx-joe-ourlib-xx

2.2: Okapi接收请求:
 * 确认其它是我们已知的"ourlib"租户。
 * 构建一个服务于/motd的模块列表，并为"ourlib"启用这些模块。
 * 列表通常由两个模块组成：第一个是"auth"模块，随后是"motd"模块。
 * Okaip会注意到请求包含一个X-Okapi-Token，和之前一样，它会保存这个token以便后面使用。
 * Okapi查看这些模块的moduleDescriptors并检查它们需要和期望哪些权限。auth模块不需要任何权限。motd模块需要"motd.show"权限，期望"motd.staff"权限。它将这些值放入X-Okapi-Permissions-Required和-Desired的消息头中。
 * Okapi检查moduleDescriptors并确认模块是否已经赋予了权限。motd模块有"db.motd.read"权限。Okapi将这个权限写入X-Okapi-Module-Permissions这个消息头中。
 * Okapi确认auth模块具体运行的位置(哪一个节点上的哪一个端口)。
 * Okapi把这些额外的消息头发送给auth模块：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: [ "motd.show" ]
     * X-Okapi-Permissions-Desired: [ "motd.staff" ]
     * X-Okapi-Module-Permissions: { "motd": "db.motd.read" }
      

2.3：auth模块接收请求：
 * 确认我们是否拥有X-Okapi-Token消息头。
 * 校验令牌的签名
 * 提取用户ID和租户ID
 * 和之前一样，确认租户ID是否与消息头X-Okapi-Tenant的中匹配。
 * 查看我们需要和期望的权限，由于它并没有关于joe账户权限的缓存，所以这些权限需要从权限模块中获得。
 * 向 /permissions/joe发出请求。权限模块本身不需要为读取提供任何特殊的权限，至少在用户查找自己权限的时候。
 * 向Okapi发出请求：
     * GET `http://folio.org/okapi/permissions/joe`
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx

2.4：Okapi接受请求，并像1.2节那样处理它：
 * 确认我们已知的租户"ourlib"
 * 构建一个 /permissions/:id 的模块列表，并且为"outlib"启用这些模块
 * 这个列表通常由两个模块组成:首先是"auth"模块，然后是"perm"模块。
 * Okapi注意到请求包含一个X-Okapi-Token后会保存该令牌以备将来所用。
 * Okapi检查这些服务需要哪些权限以及期望哪些权限。对于简单的查询，权限模块不需要权限，否则就会陷入无休止的递归，这一点很重要。
 * 权限模块本身没有理由不具有模块级别的权限。例如，"db.permission.read"。这里我们假设没有。
 * Okapi确认auth模块运行的位置(哪个节点、哪个端口)。
 * Okapi将这些额外的请求头传递给auth模块：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: [ ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { }
 
2.5：auth模块像之前1.3节那样校验JWT：
 * 使用新得消息头向Okapi发送一个Ok响应：
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { }

2.6：Okapi像之前1.4节那样，在没有任何特殊权限的情况下接收OK响应：
 * Okapi会注意到下一个调用模块是 perm模块。
 * 确认perm模块在哪里运行。
 * 向perm模块发出带有如下信息头的请求：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions: [ ]

2.7: 权限模块获取请求：
 * 为"joe"用户查询权限。
 * 返回一个带有权限列表的Ok响应，如：[ "motd.show", "motd.staff", "what.ever.else" ]。

2.8：Okapi接收Ok响应:
 * 它看到列表中没有更多的模块。
 * 于是将响应返回给auth模块。

2.9: auth模块接收来自perm模块的响应，并继续后续处理：
 * 它会注意到我们一个带有"motd.show"的X-Okapi-Permissions-Required消息头。
 * 由于接收道德JWT没有特殊的模块权限，所以它使用joe的权限列表。
 * 可能希望缓存joe的权限列表以便下次使用。
 * 确认joe的权限列表中是否包含相应的权限。如果没有它会立刻给Okapi返回一个错误响应，随后Okapi会把这个响应返回给UI并终止后续处理。
 * 它发现我们有一个带有"motd.staff"的X-Okapi-Permissions消息头，于是将这个权限添加进X-Okapi-Permissions消息头的列表中。
 * 之后，auth模块会发现我们的X-Okapi-Module-Permisson中
 * auth模块看到我们有一个"motd"的X-Okapi-Module-Permissions消息头。
 * 于是它创建一个包含原来所有JWT信息和一个新的取值为"motd"的"modulePermissions"的新的JWT。我们假设令牌类似于 xx-joe-outlib-motd-xx。
 * 将这个新的JWT放入X-Okapi-Module-Tokens的消息头中。
 * 最后返回一个带有如下消息头的Ok响应：
     * X-Okapi-Permissions: [ "motd.staff" ]
     * X-Okapi-Module-Tokens: { "motd" : "xx-joe-ourlib-motd-xx" }
 
注意，应为"motd.show"权限是必须的，所以模块没有必要将其返回。如果joe并没有这个权限，那么auth模块将会认证失败，并且motd模块永远不会被调用。

2.10：Okapi 接收来自auth模块的Ok响应：
 * 它会注意到它接受了一个X-Okapi-Module-Tokens的消息头。这表明已经完成授权，所以它会跟之前一样，将X-Okapi-Permissions-Required和-Desired消息头从之后的请求中移除。
 * 这里有一个"motd"的模块令牌。Okapi会将该令牌存储在其要调用的模块列表中的motd模块中。
 * 它会注意到下一个调用的模块就是motd模块
 * 确认motd模块在哪里运行。
 * 查看我们有一个motd的模块令牌。
 * 向motd模块发出带有如下消息头的请求：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-motd-xx
     * X-Okapi-Permissions: [ "motd.staff" ]

2.11：motd模块接收来自Okapi的请求：
 * 它发现X-Okapi-Permissions包含 "motd.staff" 权限。于是，它决定从数据库中检索staff-only的今日消息(MOTD)。
 * 发送一个带有如下消息头的GET请求到`http://folio.org/okapi/db/motd/staff`：
    * X-Okapi-Tenant: ourlib
    * X-Okapi-Token: xx-joe-ourlib-motd-xx

2.12：Okapi接收请求，并和之前一样进行处理：
 * 确认我们已知的租户"ourlib"。
 * 构建一个服务于 /db/motd/ 的模块列表，并且为"outlib"启用这些模块
 * 列表通常由两个模块组成：第一个是"auth"模块，随后是"db"模块。
 * Okaip会注意到请求包含一个X-Okapi-Token，它会保存这个token以便后面使用。
 * Okapi查看这些服务必须和期望的权限。db模块需要"db.motd.read"的权限。
 * 为了简单起见，我们假设db模块没有任何特定权限。
 * Okapi确认auth模块在哪里运行（哪个节点及对其对应的端口）。
 * Okapi把带有以下额外消息头的请求传递给auth模块：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-motd-xx
     * X-Okapi-Permissions-Required: [ "db.motd.read" ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { }

2.13：auth模块接收请求：
 * 确认我们已经拥有一个X-Okapi-Token的消息头。
 * 校验令牌的签名。
 * 从请求中提取租户ID和用户ID。
 * 和之前一样，确认租户ID是否与消息头X-Okapi-Tenant的中匹配。
 * 它会查看我们需要和期望的一些权限。现在我们已经缓存了joe的权限列表，所以会使用那个列表。
 * 它会发现JWT中有一个特殊的modulePermissions，于是会把"db.motd.read"添加到权限列表中。
 * 它会发现我们已经有一个带有"db.motd.read"的X-Okapi-Permissions消息头。确认权限列表中是否已经有这个权限。
 * 没有看到任何期望的权限。
 * 没有看到任何关于db模块的模块权限。
 * 由于JWT包含一个特殊的权限，auth模块需为之后调用的不同模块创建一个新的没有包含特殊权限的JWT。它将modulePermissions从JWT中移除，并生成相应的签名。其结果和最初的JWT相同，"xx-joe-ourlib-xx"。它在X-Okapi-Module-Tokens中为特殊模块"_"返回该值。
 * 给Okapi返回一个带有如下消息头的Ok响应：
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { "_" : "xx-joe-ourlib-xx" }
 
2.14：Okapi接收来自auth的Ok响应：
 * 看到"_"有一个特殊的模块令牌。将令牌复制到它打算调用的所有模块，覆盖具有motd的特殊权限的令牌。这样这些权限就不会泄露给其他的模块。
 * 看到没有模块令牌。
 * 看到后续模块是db模块。
 * 向db模块发送一个带有如下消息头的请求：
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx

2.15：db模块接收请求：
 * 为员工查找今日消息(MOTD)。
 * 将查询结果在OK响应中返回。

2.16：Okapi接收OK响应：
 * 由于模块列表中没有其他模块，所以它将返回Ok响应。

2.17：motd模块接收来自db模块的Ok响应:
 * 构建一个自己带有来自DB消息的Ok响应。
 * 将其返回给Okapi。

2.18: Okapi接收Ok响应：:
 * 因为motd模块是模块列表中最后一个模块，所以，它将给调用方返回响应。

2.19: UI显示今日消息。

### 登录

这里简要介绍一下当一个用户登录系统时会发生什么——详细而乏味的握手。

* UI发送一个登录请求给Okapi。这个时候还没有JWT。

* Okapi把这个请求传递给auth模块。
* auth模块发现我们没有JWT，于是创建了一个JWT。
* 它同时也会为认证模块创建一个带有模块级别权限的JWT。
* Okapi使用这个拥有发出其他请求权限的JWT调用认证模块。
* 认证模块为用户生成一个真正的JWT而调用授权模块
* 将这个JWT返回给UI并且使用。
* 这个JWT返回给UI，并在后续的调用中使用。

在这个例子中，登录使用的是简单的用户名/密码登录，但是其他模块可以检查LDAP服务器、OAuth或任何其他身份验证方法。

3.1：启动UI，并以某种方式向用户展示登录界面:
 * 用户输入凭证，在这个例子中使用的是用户名/密码。
 * 点击提交按钮。

3.2: UI向Okapi发送一个请求:
 * POST `http://folio.org/okapi/authn/login`
 * X-Okapi-Tenant: ourlib
 * 当然我们还没有JWT。
 
注意，那个URL和租户ID必须以某种方式为UI所知。

3.3: Okapi接收请求:
 * 确认我们已知租户"outlib"
 * 构建一个服务于/authn/login的模块列表，并为租户"ourlib"启用这些模块
 * 这个列表通常由两个模块组成:首先是"auth"模块，然后是"login"模块。
 * Okapi会注意到请求包含X-Okapi-Token的消息头。
 * Okapi会查看这些模块的moduleDescriptors，并确认必须和期望的权限。显然，在这里我们不需要任何的服务，因为登录服务必须开启的。
 * Okapi检查moduleDescriptors，以确认是否向任何模块授予了任何权限。登录模块至少需要"db.user.read.passwd"和"auth.newtoken"这两个权限，或许还需要更多。Okapi将权限放入X-Okapi-Module-Permissions消息头中。
 * Okapi确认auth模块在哪里运行(哪个节点的哪个端口)
 * Okapi把带有如下额外消息头的请求传递给aut模块:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Permissions-Required: [ ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { "login": [ "auth.newtoken", "db.user.read.passwd" ] }

3.4: auth接收请求:
 * 它会发现我们没有X-Okapi-Token。
 * 于是它会用X-Okapi-Tenant的租户ID创建一个，但是并不包含用户ID。我们把它叫做 "xx-unknown-ourlib-xx"。在模块“_”下返回这个通用令牌。
 * 我们没有要求其他权限。
 * 我们没有模块权限。
 * 它为X-Okpai-Module-Permissions中列出的login模块创建一个新的JWT——"xx-unknown-ourlib-login-xx"，其中包含"auth.newtoken"和"db.user.read.passwd"
 * 向Okapi发送一个带有如下消息头的Ok响应:
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx",
"login" : "xx-unknown-ourlib-login-xx" }

3.5: Okapi接收来自auth的OK响应:
 * 查看到我们已经拥有模块权限，Okapi会拷贝这些权限到后续调用的模块列表中。"login"模块将获得"xx-unknown-ourlib-login-xx"，其余所有模块(已经调用的auth模块)将获得“x-unknown-ourlib-xx”。
 * 查看到下一个模块是 "login"
 * 向login模块发送如下请求：
     * 接收到的请求实体
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx

3.6: Login模块接收请求:
 * 它发现它有用户名和密码，于是它需要从数据库中查询数据。
 * 它创建一个请求:
     * GET `http://folio.org/okapi/db/users/joe/passwd`
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx

3.7: Okapi接收并像2.12那样处理:
 * 确认我们已知的租户"ourlib"。
 * 构建一个服务于 /db/users/ 的模块列表，并且为"outlib"启用这些模块。
 * 表通常由两个模块组成：第一个是"auth"模块，随后是"db"模块。
 * Okaip会注意到请求包含一个X-Okapi-Token，它会保存这个token以便后面使用。
 * Okapi查看这些服务必须和期望的权限。db模块需要"db.motd.read"的权限。b模块需要"db.user.read.passwd"的权限。
 * 为了简单起见，我们假设db模块没有任何特定权限。
 * Okapi把带有以下额外消息头的请求传递给auth模块:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx
     * X-Okapi-Permissions-Required: [ "db.motd.read" ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { }

3.8: auth模块接收请求:
 * 确认我们已经有一个X-Okapi-Token的消息头。
 * 校验token的签名。
 * 从token中提取租户ID。这里没有用户ID。
 * 和之前一样，校验租户ID是否与X-Okapi-Tenant中的匹配。
 * 它发现我们需要一些权限。由于JWT中没有任何用户，所以它无法查找任何权限。
 * 它发现JWT中具有特殊的模块权限。它将"db.user.read.passwd"和"auth.newtoken"添加到权限列表中（这时的列表应该是空的）。
 * 它发现我们有个包含"db.user.read.passwd"的X-Okapi-Permissions-Required"的消息头。检查权限列表并查找其相应的位置。
 * 没有发现任何期望的权限。
 * 没有发现db模块所需的模块权限。
 * 因为JWT中已有特殊的模块权限，auth模块需要创建一个新的不包含该权限的JWT以便后续调不同的模块。它将modulePermissions从JWT中移除，并生成签名。其结果和原始的JWT一样——"xx-unknown-ourlib-xx"。它将结果在X-Okapi-Module-Tokens中作为"_"的特殊模块令牌。
 * 将带有如下消息头的OK响应返回给Okapi:
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx" }

3.9: Okapi接收来自auth模块的OK响应:
 * 发现"_"有一个特殊模块令牌。将该令牌复制到它打算调用的所有模块，覆盖具有登录特殊权限的令牌。
 * 发现这里没有其他的模块令牌
 * 发现下一个模块是db模块。
 * 向db模块发送一个请求:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-xx

3.10: db模块接收请求:
 * 它查找"joe"的密码的hash值。
 * 将查询结果在OK响应中返回。

3.11: Okapi接收OK响应:
 * 由于列表中没有更多的模块，它将Ok响应返回给login模块。

3.12: login模块接收含有密码哈希值的OK响应:
 * 校验密码的哈希值是否与用户输入的相同
 * 这时我们已经完成了用户认证。
 * 接下来login模块需要为用户创建一个JWT。它自己并不生成JWT，而是请求auth模块来生成。它向auth模块发送一个如下请求:
     * POST `http://folio.org/okapi/auth/newtoken`
     * 请求内容包含用户名
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx

3.13: Okapi接收请求并像3.7一样处理请求:
 * 确认我们已知的租户"ourlib"。
 * 构建一个服务于 /auth/newtoken/ 的模块列表，并且为"outlib"启用这些模块。
 * 表通常由两个模块组成：第一个是"auth"模块，随后是路径为/newtoken的"auth"模块。
 * Okapi注意到请求中包含X-Okapi-Token。它将令牌保存起来以便后续使用。
 * Okapi确认服务需要和期望的权限。路劲为/newtoken的auth模块需要"auth.newtoken"的权限。
 * auth模块自身并不需要任何特殊的权限。
 * Okapi将请求传递给auth模块并附上如下额外的消息头:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx
     * X-Okapi-Permissions-Required: [ "auth.newtoken" ]
     * X-Okapi-Permissions-Desired: [ ]
     * X-Okapi-Module-Permissions: { }

3.14: auth模块接收请求:
 * 它确认我们有一个X-Okapi-Token的消息头。
 * 校验令牌的签名。
 * 从令牌中提取租户ID。此时依旧没有用户ID。
 * 校验租户ID是否与X-Okapi-Tenant消息头中的匹配。
 * 它发现我们需要和期望一些权限。因为我们的JWT中并没有任何用户，所以它并能查找任何权限
 * 它看到JWT在其中具有特殊的模块权限。于是将"db.user.read.passwd"和"auth.newtoken"添加到权限列表中(此时列表应该是空的)。
 * 它发现我们有个包含"auth.newtoken"的X-Okapi-Permissions-Required的消息头。它会检查权限列表，并查找该全新的位置。
 * 没有发现任何期望的权限。
 * 没有发现db模块需要任何模块权限。
 * 因为JWT包含特殊的模块权限，auth模块需要为后续调用其他模块而创建一个不包含该权限的新的JWT。它将modulePermissions从JWT中移除，并签名。其结果与源JWT相同——"xx-unknow-outlib-xx"。它在X-Okapi-Module-Tokens中返回该值作为特殊模块"_"的令牌。
 * 将带有如下消息头的OK响应返回给Okapi:
     * X-Okapi-Permissions: [ ]
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx" }

3.15: Okapi接收来自auth模块的OK响应:
 * 发现有"_"的特殊模块令牌。将该令牌复制到它打算调用的所有模块中，覆盖具有登录特殊权限的令牌。
 * 发现这里并没有模块令牌。
 * 发现下一个调用模块还是auth模块，而此时的auth模块的路径是/newtoken。
 * 向auth模块发送一个请求:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-xx
     * 带有用户名的原始数据
     
3.16: auth模块接收请求:
 * 用请求中的用户名(username)和消息头中的租户(tenant)生成一个JWT——"xx-joe-ourlib-xx"。
 * 将JWT在OK响应中返回。

3.17: Okapi接收OK响应:
 * 由于列表中没有更多的模块，它将返回登录模块的OK响应。

3.18: 此时，登录模块可以查看它是否接收到了用户的新的权限，例如来自LDAP后端,在这种情况下，向权限模块发送一个新的请求来更新权限。它需要有这样做的权限，并且与Okapi像之前查询数据库一样(3.7-3.11)通信。

3.19: login模块返回一个带有新的JWT的OK响应。

3.20: Okapi发现没有后续调用模块，于是将OK响应返回给UI。

3.21: UI保存JWT以便后续调用时使用。

## 开放式问题和技术细节

* 我们必须注意到在newtoken情况下auth模块会被调用两次。它会看到两个相同的请求。区分两者的一个简单方法便是是否含有"X-Okapi-Module-Permissions"消息头。如果有则表明是常规授权检查的请求；如果没有，其请求路径将显示这是对新令牌的请求，还是auth模块提供的其他服务。
* 实际上，将会有比上面例子中提到的更多的验证和错误处理。
* JWT属于auth模块，只有auth模块可以创建或修并进行签名，因为只有JWT拥有签名密钥。不幸的是，这个密钥需要在同一集群上的auth模块的所有实例之间共享。但是第一个启动的程序仍然可以随机生成一个并与其他程序共享，或者我们可以为每个安装使用硬编码值。
* auth模块可以自由地将其他内容放入JWT中。例如，它可以保存某种类型的sessionId用于日志记录，或者原始请求到达时的时间戳，以便对整个请求序列进行计时和跟踪。

## Okapi的处理

上面的示例显示了通过Okapi代码的各种路径。这里我们总结了Okapi需要做的所有操作:

 * Okapi确认我们是否有一个X-Okapi-Tenant消息头, 并且这里提到的租户是确实存在的。
 * 它构建一个服务于该请求的模块列表，并为租户启用这些模块。这些将按照ModuleDescriptor中的RoutingEntry中的"level"来排序。
 * 这个列表通常由两个模块组成:首先是"auth"，然后是服务于请求的实际模块。但是我们可能会涉及到更多的模块，可能是对每个请求进行后处理后的某种日志模块。
 * Okapi确认是否包含X-Okapi-Token。如果有，它会将其保存起来以便后续使用。事实上，它会将其复制到模块管道中的每个入口中。
 * Okapi遍历其计划所调用的RoutingEntry，并且为服务收集必要的（permissionsRequired）和期望的（permissionsDesired）权限 。 它将列表进行复并将他们以JSON字符串列表放入X-Okapi-Permissions-Required 和 X-Okapi-Permissions-Desired中。具体如下所示: [ "some.perm", "other.perm" ]
 * Okapi会遍历其计划传递的模块的 moduleDescriptor，并确认是否向任何模块授予了任何权限。它将那些信息放入X-Okapi-Module-Permissions中以JSON格式（以Map方式保存，key为模块名，value为权限列表）进行传递。具体如下所示:
{ "motd" : [ "db.motd.read" ], "foo" : [ "bar.x", "bar.y" ] }
 * 这时Okapi可以开始向模块发送请求了。对于其管道列表中的每个模块会做如下操作:
 * 确认模块在哪里运行(节点位置及端口)。
 * 在原始的对魔窟的HTTP中附加上额外的消息头:
     * X-Okapi-Tenant: 和接收到的一样。
     * X-Okapi-Token: 要么是接收到的，要么是从auth模块中获取一个新的。
     * X-Okapi-Permissions-Required:从RoutingEntries中收集。
     * X-Okapi-Permissions-Desired: 从RoutingEntries中收集。
     * X-Okapi-Module-Permissions: 从ModuleDescriptions中收集。
 * 当接收到模块响应时，Okapi会确认其是否是一个OK响应。如果不是，它会把错误响应返回给调用方并终止后续处理。
 * 之后Okapi会确认响应是否包含消息头 X-Okapi-Module-Tokens ，这意味着模块已经完成认证。如果是这种情况Okapi会进行如下操作:
     *
This indicates that the module has done auth checks.
If that is the case, then Okapi:
     * 查看是否有模块"_"的令牌。如果有，则将令牌拷贝到诶一个计划调用的模块。
     * 查看是否有指定模块的令牌，如果有则将其拷贝到相应的模块。
     * 移除消息头: X-Okapi-Permissions-Required、
X-Okapi-Permissions-Desired 和 X-Okapi-Module-Permissions。 这些消息头的目的已经达到。
 * 当Okapi处理完列表中的所有模块，它将最后的一次的响应返回给调用方。


## auth的处理
在上述例子中，我们看到了auth(授权)模块所做的各种事情。这里我们试着按照正确的顺序来总结下。当auth模块接收到请求时:
 * 确认我们有消息头X-Okapi-Tenant header。这个消息头始终只有一个。因为缺少这个消息头会使得Okapi的请求失败，所以遇到问题的时候最好确认下。
 * 创建一个模块令牌的空的映射表
 * 确认我们是佛有一个X-Okapi-Token的消息头。如果没有，它会用X-Okapi-Tenant中的租户来创建一个，没有用户名，没有modulePermissions。将其保存在牌映射表中的键"_"下。
 * 如果有X-Okapi-Token，它会做如下校验:
     * 确认签名和内容是否匹配。
     * 确认租户与X-Okapi-Tenant中的租户匹配，这样就能保证始终指向的是正确的租户。
     * 如果其中任何一个失败，它将立即拒绝请求，并返回一个400“无效请求”和一个可见的无效原因。
     * 从令牌中提取用户ID（如果有的话）
 * 之后auth模块会确认X-Okapi-Permissions-Required
或 X-Okapi-Permissions-Desired中是否有内容:
     * 如果我们有用户ID，它要么从权限模块获取用户的权限列表，要么从它自己的缓存(如果没有过期的话)获取用户的权限列表。
     * 确认JWT中是否包含modulePermissions。如果包含则将其添加到用户的权限列表中。
     * 对于Permissions-Required中提及的每一个权限，它都会确认是否在权限列表中。如果没有，它会立刻拒绝请求，并发出 403 - Forbidden 以及一条消息显示它需要什么权限。
     * 对于Permissions-Required中提及的每一个权限，它都会确认该权限是否在权限列表中，如果有，则会将其添加到权限列表中，并在X-Okapi-Permissions中报告给模块。
 * 下一步auth模块会确认JWT是否包含modulePermissions。如果有在会创建一个与原JTW极其相似的JWT。只不过这个JWT不包含modulePermissions，并且对其进行签名，并保存到模块"_"的模块映射中。
 * 之后他会确认我们是否有X-Okpai-Module-Permissions，并对这里提到的每一个模块它都会验证模块名（包含字母和数字，而不是"\_"）。然后接受"_"的JWT将这些权限添加进去，进行签名，并且将其存储在模块名下的映射中。
 * 他将模块令以JSON编码的字符串形式映射到X-Okapi-Module-Tokens消息头中。具体如下: { "motd" : "xxx-motd-token-xxx",
"db" : "xxx-db-token-xxx", "_" : "xxx-default-token-xxx" }。 即使映射中没有任何内容，它也会这么做，这样就可以通知Okapi已经完成授权。
 * 最终，他将返回带有如下消息头的OK响应:
     * X-Okapi-Permissions:
     * X-Okapi-Module-Tokens:

