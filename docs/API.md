---
title: ProxmoxVE-AMS v1.0.0
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
code_clipboard: true
highlight_theme: darkula
headingLevel: 2
generator: "@tarslib/widdershins v4.0.17"

---

# ProxmoxVE-AMS

> v1.0.0

Base URLs:

# Authentication

- HTTP Authentication, scheme: basic

# 后台/API管理

## POST 添加API key

POST /{adminPath}/insertApiKey

新增api key，该appkey只会本次显示，后续查询将不显示

> Body 请求参数

```json
{
  "info": "一句话"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» info|body|string¦null| 否 |备注|

> 返回示例

> 成功

```json
{
  "code": 501002,
  "message": "API鉴权失败"
}
```

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "id": 2,
    "appid": "1690208651216",
    "appkey": "23ba5aa474fa416a99a404a02065f29849",
    "info": "接口2",
    "createDate": 1690208651216
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» id|integer|true|none||none|
|»» appid|string|true|none||none|
|»» appkey|string|true|none||none|
|»» info|string|true|none||none|
|»» createDate|integer|true|none||none|

## GET 分页获取API信息

GET /{adminPath}/selectApiByPage

分页获取API信息

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |每页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "appid": "1687276783870",
        "appkey": "e280bdf9e8914fd597f850d0fcdf162b42",
        "info": null,
        "createDate": null
      },
      {
        "id": 2,
        "appid": "1690208651216",
        "appkey": "23ba5aa474fa416a99a404a02065f29849",
        "info": "接口2",
        "createDate": 1690208651216
      }
    ],
    "total": 2,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» appid|string|false|none||none|
|»»» appkey|string|false|none||none|
|»»» info|null|false|none||none|
|»»» createDate|null|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## DELETE 删除指定ID的API key

DELETE /{adminPath}/deleteApi

删除指定ID的API key，可以post请求

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|id|query|integer| 否 |api id|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "删除成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## PUT 停用指定API

PUT /{adminPath}/disableApi/{id}

停用指定API

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|path|integer| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 启用指定API

PUT /{adminPath}/enableApi/{id}

启用指定API

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|path|integer| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/IP池管理

## POST 根据掩码位批量插入IP

POST /{adminPath}/insertIpPoolByMask

根据掩码位批量插入IP到IP池，并创建IP池

> Body 请求参数

```json
{
  "nodeId": 5,
  "gateway": "23.94.247.33",
  "mask": 28,
  "dns1": "114.114.114.114",
  "dns2": "8.8.8.8",
  "poolName": "美国1"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» poolName|body|string| 是 |池名|
|» nodeId|body|integer| 是 |绑定节点ID|
|» gateway|body|string| 是 |网关|
|» mask|body|integer| 是 |掩码位，如：24|
|» dns1|body|string| 是 |none|
|» dns2|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 根据IP范围批量插入IP

POST /{adminPath}/insertIpPoolByRange

根据IP范围批量插入IP到已创建的IP池

> Body 请求参数

```json
{
  "poolId": 1,
  "startIp": "192.168.1.1",
  "endIp": "192.168.1.10",
  "dns1": "114.114.114.114",
  "dns2": "8.8.8.8"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» poolId|body|integer| 是 |IP池ID|
|» startIp|body|string| 是 |起始IP|
|» endIp|body|string| 是 |结束IP|
|» dns1|body|string| 是 |dns1|
|» dns2|body|string| 是 |dns2|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询IP池列表

GET /{adminPath}/selectIpPoolList

分页查询IP池列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 2,
        "name": "美国1",
        "gateway": "23.94.247.33",
        "mask": 28,
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "available": null,
        "used": null,
        "disable": null,
        "nodeid": 5
      },
      {
        "id": 3,
        "name": "本地",
        "gateway": "192.168.1.1",
        "mask": 24,
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "available": null,
        "used": null,
        "disable": null,
        "nodeid": 5
      }
    ],
    "total": 2,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|true|none||none|
|»»» name|string|true|none||none|
|»»» gateway|string|true|none||none|
|»»» mask|integer|true|none||none|
|»»» dns1|string|true|none||none|
|»»» dns2|string|true|none||none|
|»»» available|null|true|none||none|
|»»» used|null|true|none||none|
|»»» disable|null|true|none||none|
|»»» nodeid|integer|true|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## GET 查询指定IP池下的IP列表

GET /{adminPath}/selectIpListByPoolId

查询指定IP池下的IP列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|poolid|query|integer| 否 |IP池ID|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |页数量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 17,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.33",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 18,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.34",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 19,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.35",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 20,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.36",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 21,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.37",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 22,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.38",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 23,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.39",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 24,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.40",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 25,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.41",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 26,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.42",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 27,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.43",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 28,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.44",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 29,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.45",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      },
      {
        "id": 30,
        "nodeId": 5,
        "vmId": null,
        "poolId": 2,
        "ip": "23.94.247.46",
        "subnetMask": "255.255.255.240",
        "gateway": "23.94.247.33",
        "dns1": "114.114.114.114",
        "dns2": "8.8.8.8",
        "status": 0
      }
    ],
    "total": 14,
    "size": 50,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|true|none||none|
|»»» nodeId|integer|true|none||none|
|»»» vmId|null|true|none||none|
|»»» poolId|integer|true|none||none|
|»»» ip|string|true|none||none|
|»»» subnetMask|string|true|none||none|
|»»» gateway|string|true|none||none|
|»»» dns1|string|true|none||none|
|»»» dns2|string|true|none||none|
|»»» status|integer|true|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## PUT 更新IP池信息

PUT /{adminPath}/updateIpPool

更新IP池信息，支持post请求

> Body 请求参数

```json
{
  "id": 0,
  "name": "string",
  "gateway": "string",
  "mask": 0,
  "dns1": "string",
  "dns2": "string",
  "nodeid": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string¦null| 否 |none|
|» gateway|body|string¦null| 否 |none|
|» mask|body|integer¦null| 否 |none|
|» dns1|body|string¦null| 否 |none|
|» dns2|body|string¦null| 否 |none|
|» nodeid|body|integer¦null| 否 |节点ID|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改IP信息

PUT /{adminPath}/updateIp

修改IP信息，可多个，支持post请求

> Body 请求参数

```json
[
  {
    "id": 31,
    "dns1": "114.114.114.114"
  },
  {
    "id": 408,
    "dns1": "114.114.114.114"
  }
]
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|array[object]| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除指定IP池

DELETE /{adminPath}/deleteIpPool/{poolId}

删除指定IP池

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|poolId|path|integer| 是 |ip池ID|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/OS管理

## GET 获取在线系统列表

GET /{adminPath}/selectOsByOnline

获取在线系统列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "page": 1,
    "size": 20,
    "total": 2,
    "totalPage": 1,
    "data": [
      {
        "CentOS-8-Stream-x64.qcow2": {
          "url": "http://oa.chuqiyun.com:8877/Cloud/Centos/CentOS-8-Stream-x64.qcow2",
          "cloud-int": 0,
          "nodeData": []
        }
      },
      {
        "Ubuntu-22.04-x64.qcow2": {
          "url": "http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2",
          "cloud-int": 0,
          "nodeData": [
            {
              "status": 0,
              "osId": 3,
              "size": "597MB",
              "path": "/home/images",
              "createTime": 1690030210715
            }
          ]
        }
      }
    ]
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 手动新增OS

POST /{adminPath}/insertOs

新增OS

> Body 请求参数

```json
{
  "name": "Ubuntu-22.04-x64",
  "fileName": "Ubuntu-22.04-x64.qcow2",
  "type": "linux",
  "downType": 37,
  "arch": "x86_64",
  "url": "http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2",
  "path": "default",
  "osType": "ubuntu",
  "cloud": 1
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» name|body|string| 是 |系统名称（别称，可自定义）|
|» fileName|body|string| 是 |文件全称，带后缀|
|» type|body|string| 是 |镜像类型（win，linux）|
|» arch|body|string¦null| 否 |镜像架构（默认x86_64）[x86_64,arm64,arm64,armhf,ppc64el,riscv64,s390x,aarch64,armv7l]|
|» osType|body|string¦null| 否 |镜像系统类别名称，type为linux时必须填写[centos,debian,ubuntu,alpine,fedora,opensuse,ubuntukylin,other]|
|» downType|body|integer| 是 |添加类型（0=自动下载;1=手动上传），为0时url参数不能为空|
|» url|body|string¦null| 否 |下载地址（downType为0时禁止为空）|
|» path|body|string¦null| 否 |pve节点下储存路径，值为空或default则默认为/home/images|
|» cloud|body|integer¦null| 否 |是否开启cloud-init（0=未开启，1=开启，默认为0）|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## GET 分页获取已添加OS

GET /{adminPath}/selectOsByPage

分页获取已添加OS

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |每页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 3,
        "name": "Ubuntu-22.04-x64",
        "fileName": "Ubuntu-22.04-x64.qcow2",
        "type": "linux",
        "arch": "x86_64",
        "osType": "ubuntu",
        "nodeStatus": null,
        "downType": 0,
        "url": "http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2",
        "size": "597MB",
        "path": "/home/images",
        "cloud": 1,
        "status": 0,
        "createTime": 1690030210715
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» name|string|false|none||none|
|»»» fileName|string|false|none||none|
|»»» type|string|false|none||none|
|»»» arch|string|false|none||none|
|»»» osType|string|false|none||none|
|»»» nodeStatus|null|false|none||none|
|»»» downType|integer|false|none||none|
|»»» url|string|false|none||none|
|»»» size|string|false|none||none|
|»»» path|string|false|none||none|
|»»» cloud|integer|false|none||none|
|»»» status|integer|false|none||none|
|»»» createTime|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## GET 分页带条件获取已添加os

GET /{adminPath}/selectOsByPageAndCondition

分页带条件获取已添加os，该接口为模糊匹配

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |每页数据量|
|param|query|string| 是 |匹配参数|
|value|query|string| 是 |匹配值|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 3,
        "name": "Ubuntu-22.04-x64",
        "fileName": "Ubuntu-22.04-x64.qcow2",
        "type": "linux",
        "arch": "x86_64",
        "osType": "ubuntu",
        "nodeStatus": null,
        "downType": 0,
        "url": "http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2",
        "size": "597MB",
        "path": "/home/images",
        "cloud": 1,
        "status": 0,
        "createTime": 1690030210715
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» name|string|false|none||none|
|»»» fileName|string|false|none||none|
|»»» type|string|false|none||none|
|»»» arch|string|false|none||none|
|»»» osType|string|false|none||none|
|»»» nodeStatus|null|false|none||none|
|»»» downType|integer|false|none||none|
|»»» url|string|false|none||none|
|»»» size|string|false|none||none|
|»»» path|string|false|none||none|
|»»» cloud|integer|false|none||none|
|»»» status|integer|false|none||none|
|»»» createTime|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## POST 下载镜像

POST /{adminPath}/downloadOs

下载指定id镜像到指定节点

> Body 请求参数

```json
{
  "osId": 1,
  "nodeId": 8
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» osId|body|integer| 是 |镜像id|
|» nodeId|body|integer| 是 |节点id|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 激活在线OS

POST /{adminPath}/activeOsByOnline

激活在线OS到数据库

> Body 请求参数

```json
{
  "fileName": "CentOS-8-Stream-x64.qcow2"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» fileName|body|string| 是 |镜像文件全名|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除os

DELETE /{adminPath}/deleteOs

删除os

> Body 请求参数

```json
{
  "osId": 4
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» osId|body|integer| 是 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "删除成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## PUT 修改os

PUT /{adminPath}/updateOs

修改os

> Body 请求参数

```json
{
  "id": 0,
  "name": "string",
  "fileName": "string",
  "type": "string",
  "arch": "string",
  "osType": "string",
  "downType": 0,
  "url": "string",
  "size": "string",
  "path": "string",
  "cloud": 0,
  "status": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string| 是 |系统名称（别称）|
|» fileName|body|string| 是 |镜像文件全名|
|» type|body|string| 是 |镜像类型[windows，linux]|
|» arch|body|string| 是 |镜像架构【x86_64，aarch64】|
|» osType|body|string| 是 |镜像操作系统【centos,debian,ubuntu,alpine,fedora,opensuse,archlinux等】|
|» downType|body|integer| 是 |0=url下载;1=手动上传，该字段禁止修改|
|» url|body|string| 是 |none|
|» size|body|string| 是 |镜像大小禁止修改|
|» path|body|string| 是 |路径|
|» cloud|body|integer| 是 |cloud-init【0=不使用;1=使用】|
|» status|body|integer| 是 |0:正常 1:停用 2:异常|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/登录鉴权

## POST 登陆接口

POST /{adminPath}/loginDo

登陆接口，

> Body 请求参数

```json
{
  "username": "mryunqi",
  "password": "123456"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|body|body|object| 否 |none|
|» username|body|string| 是 |none|
|» password|body|string| 是 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJwaG9uZSI6IjE4NzM5NTM3MTU5Iiwic2VjcmV0IjoiUWtkT2JTekl2NTZkbmhDMGdwUGljdGQxY0drLy85Qk8vSE1xeitYM2xVMD0iLCJleHAiOjE2ODkzNDc4MDd9.F_CnjpiI4ooN-vXHFM2yxD3wQaOqtxyla2Wy9mZNUVc"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/地区管理

## POST 增加地区

POST /{adminPath}/addArea

增加地区

> Body 请求参数

```json
{
  "name": "中国",
  "realm": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» name|body|string| 是 |地区名|
|» parent|body|integer¦null| 否 |父级节点id|
|» realm|body|integer| 是 |目录级别，0为顶级，1为子级|

> 返回示例

> 成功

```json
{
  "code": 20400,
  "message": "该地区已存在",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|null|true|none||none|

## DELETE 删除地区

DELETE /{adminPath}/deleteArea/{id}

删除地区

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|path|string| 是 |删除地区的id|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改地区

PUT /{adminPath}/updateArea

修改地区

> Body 请求参数

```json
{
  "id": 0,
  "name": "string",
  "parent": 0,
  "realm": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string| 是 |地区名|
|» parent|body|integer¦null| 否 |父级节点id|
|» realm|body|integer| 是 |目录级别，0为顶级，1为子级|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询地区

GET /{adminPath}/getAreaList

分页查询地区

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|limit|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "中国",
        "parent": 0,
        "realm": 0
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» name|string|false|none||none|
|»»» parent|integer|false|none||none|
|»»» realm|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## GET 查询指定id的地区

GET /{adminPath}/getArea

查询指定id的地区

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|query|integer| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "id": 1,
    "name": "中国",
    "parent": 0,
    "realm": 0
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» id|integer|true|none||none|
|»» name|string|true|none||none|
|»» parent|integer|true|none||none|
|»» realm|integer|true|none||none|

## PUT 添加某节点到指定地区

PUT /{adminPath}/addNodeToArea

添加某节点到指定地区

> Body 请求参数

```json
{
  "id": 0,
  "area": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |节点id|
|» area|body|integer| 是 |地区id|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## GET 获取指定地区的节点列表

GET /{adminPath}/getNodeListByArea

获取指定地区的节点列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|area|query|integer| 是 |地区分类ID|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 8,
        "name": null,
        "area": 1,
        "host": "106.126.12.96",
        "port": 8006,
        "username": "root",
        "password": "**********",
        "realm": "pam",
        "status": 0,
        "csrfToken": "**********",
        "ticket": "**********",
        "nodeName": "pve",
        "autoStorage": "local-lvm",
        "sshPort": 22,
        "sshUsername": "root",
        "sshPassword": "**********",
        "controllerStatus": 0
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» name|null|false|none||none|
|»»» area|integer|false|none||none|
|»»» host|string|false|none||none|
|»»» port|integer|false|none||none|
|»»» username|string|false|none||none|
|»»» password|string|false|none||none|
|»»» realm|string|false|none||none|
|»»» status|integer|false|none||none|
|»»» csrfToken|string|false|none||none|
|»»» ticket|string|false|none||none|
|»»» nodeName|string|false|none||none|
|»»» autoStorage|string|false|none||none|
|»»» sshPort|integer|false|none||none|
|»»» sshUsername|string|false|none||none|
|»»» sshPassword|string|false|none||none|
|»»» controllerStatus|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## GET 获取指定父级地区的子地区列表

GET /{adminPath}/getAreaListByParent

获取指定父级地区的子地区列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|parent|query|integer| 否 |父级ID|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询父级（一级）地区

GET /{adminPath}/getAreaListByParentIsNull

分页查询纯父级（一级）地区

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/节点池管理

## POST 添加PVE主控节点

POST /admin/insertNodeMaster

添加PVE主控集群节点

> Body 请求参数

```json
{
  "host": "23.94.247.39",
  "port": 8006,
  "username": "root",
  "password": "chuqis434..",
  "nodeName": "chuqis",
  "sshPort": 22,
  "sshUsername": "root",
  "sshPassword": "chuqis434.."
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» name|body|string¦null| 否 |名称|
|» area|body|integer¦null| 否 |地区id|
|» host|body|string| 是 |地址|
|» port|body|integer| 是 |端口|
|» username|body|string| 是 |用户名|
|» password|body|string| 是 |密码|
|» realm|body|string¦null| 否 |权限，默认pam|
|» nodeName|body|string¦null| 否 |指定节点名，默认pve|
|» sshPort|body|string| 是 |ssh端口|
|» sshUsername|body|string| 是 |ssh登录账号|
|» sshPassword|body|string| 是 |ssh登录密码|
|» status|body|string¦null| 否 |0正常1停止|
|» controllerPort|body|integer¦null| 否 |被控端口，默认7600|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功！"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## GET 分页获取集群节点列表

GET /{adminPath}/selectNodeByPage

分页获取集群节点列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |每页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "host": "192.168.36.135",
        "port": 8006,
        "username": "root",
        "password": "**********",
        "realm": "pam",
        "status": 0,
        "csrfToken": "**********",
        "ticket": "**********",
        "nodeName": "pve",
        "autoStorage": "local-lvm",
        "sshPort": 22,
        "sshUsername": "root",
        "sshPassword": "**********",
        "controllerStatus": 0
      },
      {
        "id": 4,
        "host": "oa.chuqiyun.com",
        "port": 8006,
        "username": "root",
        "password": "**********",
        "realm": "pam",
        "status": 1,
        "csrfToken": "**********",
        "ticket": "**********",
        "nodeName": "pve",
        "autoStorage": "local-lvm",
        "sshPort": 2222,
        "sshUsername": "root",
        "sshPassword": "**********",
        "controllerStatus": null
      },
      {
        "id": 5,
        "host": "23.94.247.39",
        "port": 8006,
        "username": "root",
        "password": "**********",
        "realm": "pam",
        "status": 1,
        "csrfToken": "**********",
        "ticket": "**********",
        "nodeName": "chuqis",
        "autoStorage": "local-lvm",
        "sshPort": 22,
        "sshUsername": "root",
        "sshPassword": "**********",
        "controllerStatus": null
      },
      {
        "id": 6,
        "host": "121.62.61.89",
        "port": 8006,
        "username": "root",
        "password": "**********",
        "realm": "pam",
        "status": 1,
        "csrfToken": "**********",
        "ticket": "**********",
        "nodeName": "pve",
        "autoStorage": "local-lvm",
        "sshPort": 22,
        "sshUsername": "root",
        "sshPassword": "**********",
        "controllerStatus": 0
      }
    ],
    "total": 4,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|true|none||none|
|»»» host|string|true|none||none|
|»»» port|integer|true|none||none|
|»»» username|string|true|none||none|
|»»» password|string|true|none||none|
|»»» realm|string|true|none||none|
|»»» status|integer|true|none||none|
|»»» csrfToken|string|true|none||none|
|»»» ticket|string|true|none||none|
|»»» nodeName|string|true|none||none|
|»»» autoStorage|string|true|none||none|
|»»» sshPort|integer|true|none||none|
|»»» sshUsername|string|true|none||none|
|»»» sshPassword|string|true|none||none|
|»»» controllerStatus|integer¦null|true|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## PUT 修改集群节点信息

PUT /{adminPath}/updateNodeInfo

修改集群节点信息

> Body 请求参数

```json
{
  "id": 0,
  "host": "string",
  "port": 0,
  "username": "string",
  "password": "string",
  "realm": "string",
  "status": 0,
  "nodeName": "string",
  "sshPort": 0,
  "sshUsername": "string",
  "sshPassword": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» host|body|string| 是 |none|
|» port|body|integer| 是 |none|
|» username|body|string| 是 |none|
|» password|body|string| 是 |none|
|» realm|body|string| 是 |none|
|» status|body|integer| 是 |none|
|» nodeName|body|string| 是 |none|
|» sshPort|body|integer| 是 |none|
|» sshUsername|body|string| 是 |none|
|» sshPassword|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除指定ID节点

DELETE /{adminPath}/deleteNodeById

删除指定ID节点

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|nodeId|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/节点池管理/网卡管理

## GET 获取节点网卡信息

GET /{adminPath}/getPveNodeNetworkInfo

获取节点网卡信息

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|nodeId|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取节点网卡配置文件信息

GET /{adminPath}/getPveNodeInterfaces

获取节点网卡配置文件信息

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|nodeId|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/节点池管理/负载信息

## GET 查询单个节点状态

GET /{adminPath}/getNodeInfoByOne

查询单个节点状态

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|nodeId|query|integer| 是 |节点ID|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "cpuinfo": {
      "flags": "fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc arch_perfmon rep_good nopl xtopology cpuid tsc_known_freq pni pclmulqdq vmx ssse3 cx16 pdcm pcid sse4_1 sse4_2 x2apic popcnt tsc_deadline_timer aes xsave avx f16c rdrand hypervisor lahf_lm cpuid_fault pti ssbd ibrs ibpb stibp tpr_shadow vnmi flexpriority ept vpid fsgsbase tsc_adjust smep erms xsaveopt arat umip arch_capabilities",
      "model": "Intel(R) Xeon(R) CPU E5-2670 v2 @ 2.50GHz",
      "sockets": 2,
      "user_hz": 100,
      "mhz": "2499.998",
      "cpus": 20,
      "hvm": "1",
      "cores": 20
    },
    "wait": 0.00108725944859892,
    "memory": {
      "free": 28075683840,
      "total": 33649070080,
      "used": 5573386240
    },
    "idle": 0,
    "ksm": {
      "shared": 0
    },
    "loadavg": [
      "16.00",
      "16.06",
      "16.10"
    ],
    "kversion": "Linux 5.15.102-1-pve #1 SMP PVE 5.15.102-1 (2023-03-14T13:48Z)",
    "cpu": 0.803174086957861,
    "rootfs": {
      "avail": 92664442880,
      "used": 3026546688,
      "free": 97835180032,
      "total": 100861726720
    },
    "swap": {
      "total": 8589930496,
      "free": 8589930496,
      "used": 0
    },
    "pveversion": "pve-manager/7.4-3/9002ab8a",
    "uptime": 42061
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» cpuinfo|object|true|none||none|
|»»» flags|string|true|none||none|
|»»» model|string|true|none||none|
|»»» sockets|integer|true|none||none|
|»»» user_hz|integer|true|none||none|
|»»» mhz|string|true|none||none|
|»»» cpus|integer|true|none||none|
|»»» hvm|string|true|none||none|
|»»» cores|integer|true|none||none|
|»» wait|number|true|none||none|
|»» memory|object|true|none||none|
|»»» free|integer|true|none||none|
|»»» total|integer|true|none||none|
|»»» used|integer|true|none||none|
|»» idle|integer|true|none||none|
|»» ksm|object|true|none||none|
|»»» shared|integer|true|none||none|
|»» loadavg|[string]|true|none||none|
|»» kversion|string|true|none||none|
|»» cpu|number|true|none||none|
|»» rootfs|object|true|none||none|
|»»» avail|integer|true|none||none|
|»»» used|integer|true|none||none|
|»»» free|integer|true|none||none|
|»»» total|integer|true|none||none|
|»» swap|object|true|none||none|
|»»» total|integer|true|none||none|
|»»» free|integer|true|none||none|
|»»» used|integer|true|none||none|
|»» pveversion|string|true|none||none|
|»» uptime|integer|true|none||none|

## GET 获取指定节点负载信息

GET /{adminPath}/getNodeLoadAvg

获取指定节点负载信息

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|nodeId|query|integer| 是 |节点id|
|timeframe|query|string| 否 |采样时间 [hour, day, week, month, year] 默认为hour|
|cf|query|string| 否 |采样方式 [AVERAGE, MAX] 默认为AVERAGE|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": [
    {
      "memused": 1460221747.2,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "rootused": 12661586875.7333,
      "iowait": 0.0000222623823334192,
      "cpu": 0.00382233720423565,
      "time": 1690394400,
      "swapused": 0,
      "netin": 9277.89,
      "netout": 4170.07,
      "swaptotal": 7516188672,
      "loadavg": 0.0788333333333333,
      "maxcpu": 8
    },
    {
      "loadavg": 0.16,
      "netout": 2640.64,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00352859153571746,
      "netin": 6489.13666666667,
      "swapused": 0,
      "time": 1690394460,
      "rootused": 12661597593.6,
      "iowait": 0.0000201152433772258,
      "memused": 1460264072.53333,
      "memtotal": 4082933760,
      "roottotal": 25977671680
    },
    {
      "maxcpu": 8,
      "netout": 2503.01,
      "swaptotal": 7516188672,
      "loadavg": 0.1225,
      "time": 1690394520,
      "netin": 6286.4,
      "swapused": 0,
      "cpu": 0.00370552474883112,
      "iowait": 0.000022273569433985,
      "rootused": 12661607355.7333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1460735112.53333
    },
    {
      "rootused": 12661618278.4,
      "iowait": 0.0000223182657540105,
      "memused": 1461188471.46667,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "loadavg": 0.059,
      "netout": 1484.64666666667,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00344954933014918,
      "swapused": 0,
      "netin": 4368.6,
      "time": 1690394580
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1460933427.2,
      "iowait": 0.0000201275992310475,
      "rootused": 12661628313.6,
      "netin": 3949.07333333333,
      "swapused": 0,
      "time": 1690394640,
      "cpu": 0.00353498785455718,
      "maxcpu": 8,
      "loadavg": 0.136833333333333,
      "netout": 1230.68,
      "swaptotal": 7516188672
    },
    {
      "rootused": 12661611793.0667,
      "iowait": 0.0000444202458378768,
      "memused": 1460550929.06667,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "loadavg": 0.0888333333333333,
      "netout": 1248.09166666667,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00322792029388742,
      "swapused": 0,
      "netin": 3962.59166666667,
      "time": 1690394700
    },
    {
      "rootused": 12661422148.2667,
      "iowait": 0.0000200804726411833,
      "memused": 1460812322.13333,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "loadavg": 0.173833333333333,
      "netout": 1204.9,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00360637314592366,
      "netin": 3895.82333333333,
      "swapused": 0,
      "time": 1690394760
    },
    {
      "maxcpu": 8,
      "loadavg": 0.188666666666667,
      "netout": 1259.30833333333,
      "swaptotal": 7516188672,
      "swapused": 0,
      "netin": 4026.25833333333,
      "time": 1690394820,
      "cpu": 0.00328081820864399,
      "iowait": 0.000020119577572989,
      "rootused": 12661434641.0667,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459827302.4
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1460512017.06667,
      "rootused": 12661445358.9333,
      "iowait": 0.0000355780181550381,
      "netin": 4812.12666666667,
      "swapused": 0,
      "time": 1690394880,
      "cpu": 0.00348686203903096,
      "maxcpu": 8,
      "loadavg": 0.143,
      "swaptotal": 7516188672,
      "netout": 1727.12
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1460280183.46667,
      "rootused": 12661454438.4,
      "iowait": 0.0000268344062261858,
      "swapused": 0,
      "netin": 4840.17666666667,
      "time": 1690394940,
      "cpu": 0.00348081472842246,
      "maxcpu": 8,
      "loadavg": 0.117,
      "netout": 1716.21666666667,
      "swaptotal": 7516188672
    },
    {
      "cpu": 0.00338537115257298,
      "time": 1690395000,
      "swapused": 0,
      "netin": 4762.56333333333,
      "netout": 1686.9,
      "swaptotal": 7516188672,
      "loadavg": 0.0888333333333333,
      "maxcpu": 8,
      "memused": 1460690670.93333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "iowait": 0.000020110437559053,
      "rootused": 12661465156.2667
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1460439108.26667,
      "iowait": 0.0000200809581033987,
      "rootused": 12661474235.7333,
      "netin": 4986.87833333333,
      "swapused": 0,
      "time": 1690395060,
      "cpu": 0.00384946854508779,
      "maxcpu": 8,
      "loadavg": 0.0948333333333333,
      "netout": 1813.515,
      "swaptotal": 7516188672
    },
    {
      "maxcpu": 8,
      "netout": 1264.21,
      "swaptotal": 7516188672,
      "loadavg": 0.135666666666667,
      "time": 1690395120,
      "swapused": 0,
      "netin": 4127.34666666667,
      "cpu": 0.00341869542939024,
      "rootused": 12661487479.4667,
      "iowait": 0.0000200557037255743,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1460904686.93333
    },
    {
      "cpu": 0.00342220550284227,
      "swapused": 0,
      "netin": 3936.12333333333,
      "time": 1690395180,
      "loadavg": 0.155666666666667,
      "swaptotal": 7516188672,
      "netout": 1214.44333333333,
      "maxcpu": 8,
      "memused": 1460699067.73333,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "iowait": 0.0000201321484039095,
      "rootused": 12661499289.6
    },
    {
      "cpu": 0.00339834354278129,
      "time": 1690395240,
      "swapused": 0,
      "netin": 3947.40666666667,
      "swaptotal": 7516188672,
      "netout": 1227.11333333333,
      "loadavg": 0.1485,
      "maxcpu": 8,
      "memused": 1460816076.8,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "rootused": 12661509734.4,
      "iowait": 0.000022228097278553
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1460788770.13333,
      "rootused": 12661522978.1333,
      "iowait": 0.0000424081963264975,
      "time": 1690395300,
      "swapused": 0,
      "netin": 4343.10833333333,
      "cpu": 0.00324454598289135,
      "maxcpu": 8,
      "netout": 1450.75166666667,
      "swaptotal": 7516188672,
      "loadavg": 0.0788333333333334
    },
    {
      "rootused": 12661533627.7333,
      "iowait": 0.0000201349619437822,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1460016469.33333,
      "maxcpu": 8,
      "netout": 1495.845,
      "swaptotal": 7516188672,
      "loadavg": 0.116,
      "time": 1690395360,
      "swapused": 0,
      "netin": 4389.75166666667,
      "cpu": 0.00316938554694429
    },
    {
      "iowait": 0.0000221376600271083,
      "rootused": 12661544345.6,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1459774668.8,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1226.90833333333,
      "loadavg": 0.0888333333333333,
      "time": 1690395420,
      "netin": 3920.60166666667,
      "swapused": 0,
      "cpu": 0.00337180604081179
    },
    {
      "memused": 1454633915.73333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "iowait": 0.0000200929036187998,
      "rootused": 12661557316.2667,
      "cpu": 0.00341265445765107,
      "time": 1690395480,
      "swapused": 0,
      "netin": 4386.67833333333,
      "netout": 1485.995,
      "swaptotal": 7516188672,
      "loadavg": 0.178,
      "maxcpu": 8
    },
    {
      "iowait": 0.000034203835518333,
      "rootused": 12661568921.6,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1454418056.53333,
      "maxcpu": 8,
      "loadavg": 0.2645,
      "netout": 1224.945,
      "swaptotal": 7516188672,
      "swapused": 0,
      "netin": 3913.72833333333,
      "time": 1690395540,
      "cpu": 0.00327349743260443
    },
    {
      "iowait": 0.0000260519702983315,
      "rootused": 12661578205.8667,
      "memused": 1453590459.73333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "swaptotal": 7516188672,
      "netout": 1199.27166666667,
      "loadavg": 0.129833333333333,
      "maxcpu": 8,
      "cpu": 0.00327539344916387,
      "time": 1690395600,
      "swapused": 0,
      "netin": 3875.565
    },
    {
      "memused": 1454456285.86667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "iowait": 0.0000200961503403997,
      "rootused": 12661588718.9333,
      "cpu": 0.00303447450971182,
      "time": 1690395660,
      "netin": 4488.02333333333,
      "swapused": 0,
      "swaptotal": 7516188672,
      "netout": 1529.73,
      "loadavg": 0.1285,
      "maxcpu": 8
    },
    {
      "swaptotal": 7516188672,
      "netout": 1239.51166666667,
      "loadavg": 0.118666666666667,
      "maxcpu": 8,
      "cpu": 0.00291116550169208,
      "time": 1690395720,
      "swapused": 0,
      "netin": 3937.41833333333,
      "iowait": 0.0000201910125054433,
      "rootused": 12661598481.0667,
      "memused": 1454397440,
      "roottotal": 25977671680,
      "memtotal": 4082933760
    },
    {
      "loadavg": 0.1505,
      "swaptotal": 7516188672,
      "netout": 1261.55833333333,
      "maxcpu": 8,
      "cpu": 0.00321573761563484,
      "netin": 3990.625,
      "swapused": 0,
      "time": 1690395780,
      "rootused": 12661609676.8,
      "iowait": 0.0000222973553548977,
      "memused": 1454222062.93333,
      "memtotal": 4082933760,
      "roottotal": 25977671680
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1454272580.26667,
      "rootused": 12661623534.9333,
      "iowait": 0.0000155673204395252,
      "swapused": 0,
      "netin": 3855.66666666667,
      "time": 1690395840,
      "cpu": 0.00323917734671096,
      "maxcpu": 8,
      "loadavg": 0.223,
      "netout": 1187.73666666667,
      "swaptotal": 7516188672
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1454104302.93333,
      "iowait": 0.0000066717087597965,
      "rootused": 12661633297.0667,
      "time": 1690395900,
      "netin": 4383.91166666667,
      "swapused": 0,
      "cpu": 0.00354163566225203,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1461.095,
      "loadavg": 0.12
    },
    {
      "rootused": 12661643127.4667,
      "iowait": 0.000040043932983649,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1454571383.46667,
      "maxcpu": 8,
      "netout": 1578.30666666667,
      "swaptotal": 7516188672,
      "loadavg": 0.149666666666667,
      "time": 1690395960,
      "swapused": 0,
      "netin": 4752.96666666667,
      "cpu": 0.00342398384828234
    },
    {
      "iowait": 0.0000201532280036877,
      "rootused": 12661653640.5333,
      "memused": 1454409864.53333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "swaptotal": 7516188672,
      "netout": 1279.42333333333,
      "loadavg": 0.380333333333333,
      "maxcpu": 8,
      "cpu": 0.00326780099572356,
      "time": 1690396020,
      "swapused": 0,
      "netin": 3996.77
    },
    {
      "rootused": 12661662924.8,
      "iowait": 0.000020105697877345,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1454747511.46667,
      "maxcpu": 8,
      "loadavg": 0.341,
      "swaptotal": 7516188672,
      "netout": 1436.06,
      "swapused": 0,
      "netin": 4294.37333333333,
      "time": 1690396080,
      "cpu": 0.0033592061211026
    },
    {
      "maxcpu": 8,
      "netout": 1206.84,
      "swaptotal": 7516188672,
      "loadavg": 0.173666666666667,
      "time": 1690396140,
      "swapused": 0,
      "netin": 3912.99333333333,
      "cpu": 0.00334355998070473,
      "iowait": 0.0000403341408815742,
      "rootused": 12661674120.5333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1455102225.06667
    },
    {
      "cpu": 0.00345140857890087,
      "time": 1690396200,
      "netin": 5006.26333333333,
      "swapused": 0,
      "netout": 1683.95,
      "swaptotal": 7516188672,
      "loadavg": 0.109333333333333,
      "maxcpu": 8,
      "memused": 1453736618.66667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "iowait": 0.0000321204006338249,
      "rootused": 12661684087.4667
    },
    {
      "time": 1690396260,
      "swapused": 0,
      "netin": 3991.60333333333,
      "cpu": 0.00317668596795131,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1236.45,
      "loadavg": 0.114333333333333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1451170747.73333,
      "iowait": 0.0000281904023623783,
      "rootused": 12661698150.4
    },
    {
      "maxcpu": 8,
      "loadavg": 0.072,
      "swaptotal": 7516188672,
      "netout": 1250.42666666667,
      "netin": 3945.51333333333,
      "swapused": 0,
      "time": 1690396320,
      "cpu": 0.00328537705790096,
      "rootused": 12661712076.8,
      "iowait": 0.0000443899975426065,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1452513280
    },
    {
      "iowait": 0.0000355757139697739,
      "rootused": 12661722999.4667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1452747980.8,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1418.36,
      "loadavg": 0.06,
      "time": 1690396380,
      "netin": 4288.98666666667,
      "swapused": 0,
      "cpu": 0.00343211456756045
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1458938811.73333,
      "rootused": 12661733512.5333,
      "iowait": 0.0000289947214949025,
      "netin": 4476.63,
      "swapused": 0,
      "time": 1690396440,
      "cpu": 0.00310975238188951,
      "maxcpu": 8,
      "loadavg": 0.279,
      "swaptotal": 7516188672,
      "netout": 1560.20333333333
    },
    {
      "cpu": 0.00363102384843751,
      "swapused": 0,
      "netin": 3847.54333333333,
      "time": 1690396500,
      "loadavg": 0.193,
      "swaptotal": 7516188672,
      "netout": 1168.68333333333,
      "maxcpu": 8,
      "memused": 1458305024,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "rootused": 12661743069.8667,
      "iowait": 0.0000221123560642675
    },
    {
      "swaptotal": 7516188672,
      "netout": 1250.06666666667,
      "loadavg": 0.101666666666667,
      "maxcpu": 8,
      "cpu": 0.0036323144422703,
      "time": 1690396560,
      "netin": 3945.1,
      "swapused": 0,
      "rootused": 12661753309.8667,
      "iowait": 0.0000200217282922302,
      "memused": 1458331921.06667,
      "roottotal": 25977671680,
      "memtotal": 4082933760
    },
    {
      "netin": 4646.2,
      "swapused": 0,
      "time": 1690396620,
      "cpu": 0.00382896384360154,
      "maxcpu": 8,
      "loadavg": 0.0343333333333333,
      "netout": 1607.52666666667,
      "swaptotal": 7516188672,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1458428450.13333,
      "iowait": 0.000012022568365562,
      "rootused": 12661763276.8
    },
    {
      "rootused": 12661773789.8667,
      "iowait": 0.0000280500384885117,
      "memused": 1458378615.46667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "netout": 1247.00333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.0503333333333333,
      "maxcpu": 8,
      "cpu": 0.00355634391035272,
      "time": 1690396680,
      "swapused": 0,
      "netin": 3938.75
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459169962.66667,
      "rootused": 12661788262.4,
      "iowait": 0.0000334503030655873,
      "netin": 3969.64,
      "swapused": 0,
      "time": 1690396740,
      "cpu": 0.00345891419004054,
      "maxcpu": 8,
      "loadavg": 0.155,
      "swaptotal": 7516188672,
      "netout": 1205.25333333333
    },
    {
      "rootused": 12661799458.1333,
      "iowait": 0.0000312276712287017,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1458780296.53333,
      "maxcpu": 8,
      "loadavg": 0.118666666666667,
      "netout": 1892.34666666667,
      "swaptotal": 7516188672,
      "swapused": 0,
      "netin": 5756.54666666667,
      "time": 1690396800,
      "cpu": 0.00329988794393646
    },
    {
      "time": 1690396860,
      "netin": 6078.52666666667,
      "swapused": 0,
      "cpu": 0.00365651780859706,
      "maxcpu": 8,
      "netout": 2020.39333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.0733333333333333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1459402888.53333,
      "iowait": 0.0000444860844108282,
      "rootused": 12661813521.0667
    },
    {
      "swaptotal": 7516188672,
      "netout": 2062.31666666667,
      "loadavg": 0.191666666666667,
      "maxcpu": 8,
      "cpu": 0.00326083357520016,
      "time": 1690396920,
      "swapused": 0,
      "netin": 6196.67,
      "iowait": 0.0000445164916177883,
      "rootused": 12661826764.8,
      "memused": 1459591714.13333,
      "roottotal": 25977671680,
      "memtotal": 4082933760
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459265809.06667,
      "iowait": 0.0000200721223982318,
      "rootused": 12661837960.5333,
      "netin": 4791.82,
      "swapused": 0,
      "time": 1690396980,
      "cpu": 0.00321317170371637,
      "maxcpu": 8,
      "loadavg": 0.261,
      "netout": 1674.20666666667,
      "swaptotal": 7516188672
    },
    {
      "iowait": 0.0000424337399314268,
      "rootused": 12661849292.8,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459574510.93333,
      "maxcpu": 8,
      "loadavg": 0.267666666666667,
      "netout": 1223.80333333333,
      "swaptotal": 7516188672,
      "netin": 3912.41666666667,
      "swapused": 0,
      "time": 1690397040,
      "cpu": 0.00302569776479183
    },
    {
      "swapused": 0,
      "netin": 3905.35333333333,
      "time": 1690397100,
      "cpu": 0.00306810298304686,
      "maxcpu": 8,
      "loadavg": 0.14,
      "swaptotal": 7516188672,
      "netout": 1242.75333333333,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1458730325.33333,
      "iowait": 0.0000423670142731327,
      "rootused": 12661858850.1333
    },
    {
      "iowait": 0.0000342421113577971,
      "rootused": 12661870455.4667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1456680960,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1659.29666666667,
      "loadavg": 0.0693333333333333,
      "time": 1690397160,
      "swapused": 0,
      "netin": 4688.89666666667,
      "cpu": 0.00373544669938972
    },
    {
      "cpu": 0.00338837774750435,
      "swapused": 0,
      "netin": 4939.48333333333,
      "time": 1690397220,
      "loadavg": 0.0743333333333333,
      "netout": 1709.28333333333,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "memused": 1452734190.93333,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "iowait": 0.0000281956532553454,
      "rootused": 12661883426.1333
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1453297390.93333,
      "iowait": 0.0000422879105823368,
      "rootused": 12661896396.8,
      "time": 1690397280,
      "swapused": 0,
      "netin": 3869.83666666667,
      "cpu": 0.00301773036893274,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1212.11666666667,
      "loadavg": 0.115666666666667
    },
    {
      "maxcpu": 8,
      "loadavg": 0.164333333333333,
      "swaptotal": 7516188672,
      "netout": 1339.13333333333,
      "swapused": 0,
      "netin": 4056.89666666667,
      "time": 1690397340,
      "cpu": 0.00436584387693945,
      "rootused": 12661908548.2667,
      "iowait": 0.0000635135872870228,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1453769591.46667
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1453798058.66667,
      "iowait": 0.0000301532519916783,
      "rootused": 12661918037.3333,
      "swapused": 0,
      "netin": 3849.21666666667,
      "time": 1690397400,
      "cpu": 0.00333069162397604,
      "maxcpu": 8,
      "loadavg": 0.095,
      "swaptotal": 7516188672,
      "netout": 1191.6
    },
    {
      "rootused": 12661928277.3333,
      "iowait": 0.000032423830161466,
      "memused": 1453150549.33333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "netout": 1324.33333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.0325,
      "maxcpu": 8,
      "cpu": 0.00351437059464457,
      "time": 1690397460,
      "netin": 4015.9,
      "swapused": 0
    },
    {
      "memused": 1453589845.33333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "iowait": 0.0000422951514466998,
      "rootused": 12661938176,
      "cpu": 0.00321030968532164,
      "time": 1690397520,
      "swapused": 0,
      "netin": 3952.98333333333,
      "swaptotal": 7516188672,
      "netout": 1249.71666666667,
      "loadavg": 0.0166666666666667,
      "maxcpu": 8
    },
    {
      "iowait": 0.000040154085653006,
      "rootused": 12661948757.3333,
      "memused": 1455184554.66667,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "loadavg": 0.155833333333333,
      "netout": 1340.75833333333,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00313691430710712,
      "swapused": 0,
      "netin": 4079.425,
      "time": 1690397580
    },
    {
      "cpu": 0.00315255990931396,
      "time": 1690397640,
      "swapused": 0,
      "netin": 4155.56666666667,
      "swaptotal": 7516188672,
      "netout": 1365.25,
      "loadavg": 0.1275,
      "maxcpu": 8,
      "memused": 1459301717.33333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "rootused": 12661958997.3333,
      "iowait": 0.0000322814939119781
    },
    {
      "iowait": 0.0000302025850056914,
      "rootused": 12661968896,
      "memused": 1459095893.33333,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "netout": 1165.88333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.405833333333333,
      "maxcpu": 8,
      "cpu": 0.00303527281396126,
      "time": 1690397700,
      "netin": 3759.9,
      "swapused": 0
    },
    {
      "loadavg": 0.3875,
      "netout": 1715.79166666667,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00282043568957208,
      "netin": 4790.29166666667,
      "swapused": 0,
      "time": 1690397760,
      "rootused": 12661978794.6667,
      "iowait": 0.0000445046653445802,
      "memused": 1459344384,
      "memtotal": 4082933760,
      "roottotal": 25977671680
    },
    {
      "rootused": 12661993130.6667,
      "iowait": 0.0000223081806775077,
      "memused": 1458959360,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "loadavg": 0.2875,
      "netout": 1361.09166666667,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00324201900244717,
      "swapused": 0,
      "netin": 4099.79166666667,
      "time": 1690397820
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1458840576,
      "iowait": 0.0000401411597682485,
      "rootused": 12662002688,
      "time": 1690397880,
      "swapused": 0,
      "netin": 4377.38333333333,
      "cpu": 0.00322668058589488,
      "maxcpu": 8,
      "swaptotal": 7516188672,
      "netout": 1487.43333333333,
      "loadavg": 0.1975
    },
    {
      "netout": 1753.03333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.180833333333333,
      "maxcpu": 8,
      "cpu": 0.00315210486712833,
      "time": 1690397940,
      "swapused": 0,
      "netin": 4877.53333333333,
      "rootused": 12662013952,
      "iowait": 0.0000401283600096813,
      "memused": 1459041621.33333,
      "roottotal": 25977671680,
      "memtotal": 4082933760
    },
    {
      "iowait": 0.0000222627680257857,
      "rootused": 12662023850.6667,
      "memused": 1459856042.66667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "swaptotal": 7516188672,
      "netout": 1778.75833333333,
      "loadavg": 0.235833333333333,
      "maxcpu": 8,
      "cpu": 0.00304131199905868,
      "time": 1690398000,
      "netin": 4881.025,
      "swapused": 0
    },
    {
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459446442.66667,
      "rootused": 12662034090.6667,
      "iowait": 0.0000324270556858693,
      "swapused": 0,
      "netin": 4741.64166666667,
      "time": 1690398060,
      "cpu": 0.00326805773271355,
      "maxcpu": 8,
      "loadavg": 0.254166666666667,
      "netout": 1680.19166666667,
      "swaptotal": 7516188672
    },
    {
      "maxcpu": 8,
      "loadavg": 0.283333333333333,
      "netout": 1709.71666666667,
      "swaptotal": 7516188672,
      "swapused": 0,
      "netin": 4747.45,
      "time": 1690398120,
      "cpu": 0.00300987993794617,
      "iowait": 0.0000302100264029287,
      "rootused": 12662047744,
      "memtotal": 4082933760,
      "roottotal": 25977671680,
      "memused": 1459081216
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1458882901.33333,
      "rootused": 12662057642.6667,
      "iowait": 0.000042428332419365,
      "time": 1690398180,
      "netin": 4684.65,
      "swapused": 0,
      "cpu": 0.00290581540066546,
      "maxcpu": 8,
      "netout": 1653.13333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.105833333333333
    },
    {
      "loadavg": 0.0425,
      "netout": 2202.90833333333,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00336790412495533,
      "swapused": 0,
      "netin": 5865.325,
      "time": 1690398240,
      "iowait": 0.0000201258357624042,
      "rootused": 12662068565.3333,
      "memused": 1459730432,
      "memtotal": 4082933760,
      "roottotal": 25977671680
    },
    {
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "memused": 1461247658.66667,
      "iowait": 0.0000402517071919292,
      "rootused": 12662061397.3333,
      "time": 1690398300,
      "swapused": 0,
      "netin": 8080.34166666667,
      "cpu": 0.00369067946069366,
      "maxcpu": 8,
      "netout": 3175.64166666667,
      "swaptotal": 7516188672,
      "loadavg": 0.06
    },
    {
      "iowait": 0.0000301046012230155,
      "rootused": 12661858986.6667,
      "memused": 1461068458.66667,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "netout": 3078.59166666667,
      "swaptotal": 7516188672,
      "loadavg": 0.0266666666666667,
      "maxcpu": 8,
      "cpu": 0.00323369662626955,
      "time": 1690398360,
      "netin": 7514.34166666667,
      "swapused": 0
    },
    {
      "memused": 1461747712,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "rootused": 12661871957.3333,
      "iowait": 0.0000634364352051847,
      "cpu": 0.00352719045827278,
      "time": 1690398420,
      "netin": 7008.94166666667,
      "swapused": 0,
      "swaptotal": 7516188672,
      "netout": 2835.575,
      "loadavg": 0.0941666666666667,
      "maxcpu": 8
    },
    {
      "loadavg": 0.0508333333333333,
      "netout": 4013.55,
      "swaptotal": 7516188672,
      "maxcpu": 8,
      "cpu": 0.00406956148327761,
      "netin": 8804.3,
      "swapused": 0,
      "time": 1690398480,
      "iowait": 0.0000434590877571989,
      "rootused": 12661889706.6667,
      "memused": 1462252544,
      "memtotal": 4082933760,
      "roottotal": 25977671680
    },
    {
      "cpu": 0.00398226026697113,
      "time": 1690398540,
      "swapused": 0,
      "netin": 8623.96666666667,
      "netout": 3942.73333333333,
      "swaptotal": 7516188672,
      "loadavg": 0.0166666666666667,
      "maxcpu": 8,
      "memused": 1463446528,
      "roottotal": 25977671680,
      "memtotal": 4082933760,
      "rootused": 12661902336,
      "iowait": 0.0000323621205450241
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|[object]|true|none||none|
|»» memused|number|true|none||none|
|»» roottotal|integer|true|none||none|
|»» memtotal|integer|true|none||none|
|»» rootused|number|true|none||none|
|»» iowait|number|true|none||none|
|»» cpu|number|true|none||none|
|»» time|integer|true|none||none|
|»» swapused|integer|true|none||none|
|»» netin|number|true|none||none|
|»» netout|number|true|none||none|
|»» swaptotal|integer|true|none||none|
|»» loadavg|number|true|none||none|
|»» maxcpu|integer|true|none||none|

## GET 获取节点总数

GET /{adminPath}/getNodeCount

获取节点总数

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/超管账号管理

## POST 添加超管账号

POST /{adminPath}/registerDo

添加超管账号

> Body 请求参数

```json
{
  "phone": "string",
  "password": "string",
  "email": "string",
  "username": "string",
  "name": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» phone|body|string| 是 |none|
|» password|body|string| 是 |none|
|» email|body|string| 是 |none|
|» username|body|string| 是 |none|
|» name|body|string| 是 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加管理账号成功！"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## GET 分页查询超管账号

GET /{adminPath}/getSysuser

分页查询超管账号

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |后台路径|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "username": "mryunqi",
        "password": "89cc14f2b72690dd9a7b0be672accdf9",
        "name": "夜空",
        "phone": "18739537159",
        "email": "434658198@qq.com",
        "logindate": 1691201073620
      },
      {
        "id": 2,
        "username": "admin",
        "password": "41bd67265ef3fe8bd795e3d507c3cd31",
        "name": null,
        "phone": null,
        "email": null,
        "logindate": 1691168748711
      }
    ],
    "total": 2,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|true|none||none|
|»»» username|string|true|none||none|
|»»» password|string|true|none||none|
|»»» name|string¦null|true|none||none|
|»»» phone|string¦null|true|none||none|
|»»» email|string¦null|true|none||none|
|»»» logindate|integer|true|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## POST 修改超管账号接口

POST /{adminPath}/updateSysuser

修改超管账号接口

> Body 请求参数

```json
{
  "id": 2,
  "phone": "18739537159",
  "username": "admin",
  "name": "admin",
  "password": "",
  "email": "g.mkkbfy@qq.com"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» username|body|string| 是 |none|
|» phone|body|string| 是 |none|
|» password|body|string¦null| 是 |密码为空不修改|
|» name|body|string| 是 |姓名|
|» email|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/实例管理

## POST 创建虚拟机

POST /{adminPath}/createVm

创建虚拟机

> Body 请求参数

```json
{
  "nodeid": 9,
  "hostname": "VM-01",
  "sockets": 1,
  "cores": 2,
  "threads": 2,
  "nested": false,
  "modelGroup": 2,
  "cpu": "host",
  "arch": "x86_64",
  "memory": 1024,
  "storage": "auto",
  "systemDiskSize": 40,
  "os": "CentOS-8-Stream-x64.qcow2",
  "osType": "linux",
  "bandwidth": 100,
  "ipConfig": {
    "1": ""
  },
  "dataDisk": {
    "1": 40
  },
  "onBoot": 1,
  "username": "root",
  "password": "123456"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|[VmParams](#schemavmparams)| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页获取实例信息

GET /{adminPath}/getVmByPage

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "total": 1,
    "current": 1,
    "pages": 1,
    "size": 20,
    "records": [
      {
        "vmhost": {
          "id": 25,
          "nodeid": 1,
          "vmid": 103,
          "name": "VM-01",
          "sockets": 1,
          "cores": 2,
          "threads": 2,
          "devirtualization": false,
          "kvm": true,
          "cpuModel": null,
          "modelGroup": null,
          "cpu": "host",
          "cpuUnits": 1024,
          "args": null,
          "arch": "x86_64",
          "acpi": 1,
          "memory": 1024,
          "swap": null,
          "agent": 1,
          "ide0": null,
          "ide2": "local-lvm:cloudinit",
          "net0": "virtio,bridge=vmbr0",
          "net1": null,
          "os": "CentOS-8-Stream-x64.qcow2",
          "osType": "linux",
          "iso": null,
          "template": null,
          "onBoot": 1,
          "bandwidth": 100,
          "storage": "local-lvm",
          "systemDiskSize": 40,
          "dataDisk": {
            "1": 40
          },
          "bridge": "vmbr0",
          "ipConfig": {
            "1": "ip=192.168.36.1/24,gw=192.168.36.2"
          },
          "nested": 0,
          "task": {
            "1692783654292": 135,
            "1692783654310": 136,
            "1692783693695": 137,
            "1692783694983": 138,
            "1692783695855": 139,
            "1692783696541": 140
          },
          "status": 0,
          "createTime": 1692783654293,
          "expirationTime": 2008143654293
        },
        "current": {
          "data": {
            "running-qemu": "6.1.0",
            "name": "VM-01",
            "mem": 553115648,
            "disk": 0,
            "pid": 41319,
            "status": "running",
            "diskread": 857323698,
            "proxmox-support": {
              "pbs-dirty-bitmap-savevm": true,
              "pbs-masterkey": true,
              "query-bitmap-info": true,
              "pbs-dirty-bitmap": true,
              "pbs-dirty-bitmap-migration": true,
              "pbs-library-version": "1.2.0 (6e555bc73a7dcfb4d0b47355b958afd101ad27b5)"
            },
            "agent": 1,
            "nics": {
              "tap103i0": {
                "netout": 2048408,
                "netin": 146164136
              }
            },
            "cpu": 0.0312977631257886,
            "maxdisk": 42949672960,
            "freemem": 254599168,
            "qmpstatus": "running",
            "diskwrite": 552444416,
            "blockstat": {
              "scsi1": {
                "account_invalid": true,
                "wr_operations": 0,
                "rd_merged": 0,
                "unmap_total_time_ns": 0,
                "unmap_bytes": 0,
                "unmap_merged": 0,
                "rd_total_time_ns": 119191813,
                "wr_bytes": 0,
                "timed_stats": [],
                "failed_unmap_operations": 0,
                "failed_flush_operations": 0,
                "idle_time_ns": 66957079181668,
                "invalid_wr_operations": 0,
                "unmap_operations": 0,
                "invalid_unmap_operations": 0,
                "flush_total_time_ns": 0,
                "failed_rd_operations": 0,
                "wr_highest_offset": 0,
                "rd_operations": 434,
                "account_failed": true,
                "wr_merged": 0,
                "invalid_flush_operations": 0,
                "failed_wr_operations": 0,
                "flush_operations": 0,
                "invalid_rd_operations": 0,
                "wr_total_time_ns": 0,
                "rd_bytes": 9629696
              },
              "ide2": {
                "unmap_bytes": 0,
                "unmap_total_time_ns": 0,
                "rd_merged": 0,
                "account_invalid": true,
                "wr_operations": 0,
                "unmap_operations": 0,
                "invalid_wr_operations": 0,
                "failed_flush_operations": 0,
                "failed_unmap_operations": 0,
                "idle_time_ns": 66956996453557,
                "timed_stats": [],
                "wr_bytes": 0,
                "rd_total_time_ns": 31770289,
                "unmap_merged": 0,
                "invalid_flush_operations": 0,
                "wr_merged": 0,
                "account_failed": true,
                "rd_operations": 80,
                "wr_highest_offset": 0,
                "failed_rd_operations": 0,
                "flush_total_time_ns": 0,
                "invalid_unmap_operations": 0,
                "wr_total_time_ns": 0,
                "rd_bytes": 278706,
                "invalid_rd_operations": 0,
                "flush_operations": 0,
                "failed_wr_operations": 0
              },
              "scsi0": {
                "unmap_merged": 0,
                "rd_total_time_ns": 23389570377,
                "wr_bytes": 552444416,
                "failed_unmap_operations": 0,
                "failed_flush_operations": 0,
                "idle_time_ns": 18807941480,
                "timed_stats": [],
                "unmap_operations": 0,
                "invalid_wr_operations": 0,
                "account_invalid": true,
                "wr_operations": 13321,
                "rd_merged": 0,
                "unmap_total_time_ns": 0,
                "unmap_bytes": 0,
                "failed_wr_operations": 0,
                "flush_operations": 4640,
                "invalid_rd_operations": 0,
                "wr_total_time_ns": 28158216376,
                "rd_bytes": 847415296,
                "flush_total_time_ns": 2741837421,
                "invalid_unmap_operations": 0,
                "wr_highest_offset": 42018529280,
                "failed_rd_operations": 0,
                "rd_operations": 23603,
                "invalid_flush_operations": 0,
                "wr_merged": 0,
                "account_failed": true
              }
            },
            "ballooninfo": {
              "total_mem": 807714816,
              "free_mem": 254599168,
              "last_update": 1693025679,
              "major_page_faults": 6841,
              "minor_page_faults": 14395839,
              "actual": 1073741824,
              "max_mem": 1073741824,
              "mem_swapped_out": 168591360,
              "mem_swapped_in": 44277760
            },
            "uptime": 66977,
            "vmid": 103,
            "netin": 146164136,
            "cpus": 2,
            "maxmem": 1073741824,
            "netout": 2048408,
            "ha": {
              "managed": 0
            },
            "balloon": 1073741824,
            "running-machine": "pc-i440fx-6.1+pve0"
          }
        },
        "rrddata": null
      }
    ]
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» total|integer|true|none||none|
|»» current|integer|true|none||none|
|»» pages|integer|true|none||none|
|»» size|integer|true|none||none|
|»» records|[object]|true|none||none|
|»»» vmhost|object|false|none||none|
|»»»» id|integer|true|none||none|
|»»»» nodeid|integer|true|none||none|
|»»»» vmid|integer|true|none||none|
|»»»» name|string|true|none||none|
|»»»» sockets|integer|true|none||none|
|»»»» cores|integer|true|none||none|
|»»»» threads|integer|true|none||none|
|»»»» devirtualization|boolean|true|none||none|
|»»»» kvm|boolean|true|none||none|
|»»»» cpuModel|null|true|none||none|
|»»»» modelGroup|null|true|none||none|
|»»»» cpu|string|true|none||none|
|»»»» cpuUnits|integer|true|none||none|
|»»»» args|null|true|none||none|
|»»»» arch|string|true|none||none|
|»»»» acpi|integer|true|none||none|
|»»»» memory|integer|true|none||none|
|»»»» swap|null|true|none||none|
|»»»» agent|integer|true|none||none|
|»»»» ide0|null|true|none||none|
|»»»» ide2|string|true|none||none|
|»»»» net0|string|true|none||none|
|»»»» net1|null|true|none||none|
|»»»» os|string|true|none||none|
|»»»» osType|string|true|none||none|
|»»»» iso|null|true|none||none|
|»»»» template|null|true|none||none|
|»»»» onBoot|integer|true|none||none|
|»»»» bandwidth|integer|true|none||none|
|»»»» storage|string|true|none||none|
|»»»» systemDiskSize|integer|true|none||none|
|»»»» dataDisk|object|true|none||none|
|»»»»» 1|integer|true|none||none|
|»»»» bridge|string|true|none||none|
|»»»» ipConfig|object|true|none||none|
|»»»»» 1|string|true|none||none|
|»»»» nested|integer|true|none||none|
|»»»» task|object|true|none||none|
|»»»»» 1692783654292|integer|true|none||none|
|»»»»» 1692783654310|integer|true|none||none|
|»»»»» 1692783693695|integer|true|none||none|
|»»»»» 1692783694983|integer|true|none||none|
|»»»»» 1692783695855|integer|true|none||none|
|»»»»» 1692783696541|integer|true|none||none|
|»»»» status|integer|true|none||none|
|»»»» createTime|integer|true|none||none|
|»»»» expirationTime|integer|true|none||none|
|»»» current|object|false|none||none|
|»»»» data|object|true|none||none|
|»»»»» running-qemu|string|true|none||none|
|»»»»» name|string|true|none||none|
|»»»»» mem|integer|true|none||none|
|»»»»» disk|integer|true|none||none|
|»»»»» pid|integer|true|none||none|
|»»»»» status|string|true|none||none|
|»»»»» diskread|integer|true|none||none|
|»»»»» proxmox-support|object|true|none||none|
|»»»»»» pbs-dirty-bitmap-savevm|boolean|true|none||none|
|»»»»»» pbs-masterkey|boolean|true|none||none|
|»»»»»» query-bitmap-info|boolean|true|none||none|
|»»»»»» pbs-dirty-bitmap|boolean|true|none||none|
|»»»»»» pbs-dirty-bitmap-migration|boolean|true|none||none|
|»»»»»» pbs-library-version|string|true|none||none|
|»»»»» agent|integer|true|none||none|
|»»»»» nics|object|true|none||none|
|»»»»»» tap103i0|object|true|none||none|
|»»»»»»» netout|integer|true|none||none|
|»»»»»»» netin|integer|true|none||none|
|»»»»» cpu|number|true|none||none|
|»»»»» maxdisk|integer|true|none||none|
|»»»»» freemem|integer|true|none||none|
|»»»»» qmpstatus|string|true|none||none|
|»»»»» diskwrite|integer|true|none||none|
|»»»»» blockstat|object|true|none||none|
|»»»»»» scsi1|object|true|none||none|
|»»»»»»» account_invalid|boolean|true|none||none|
|»»»»»»» wr_operations|integer|true|none||none|
|»»»»»»» rd_merged|integer|true|none||none|
|»»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»»» unmap_merged|integer|true|none||none|
|»»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»»» wr_bytes|integer|true|none||none|
|»»»»»»» timed_stats|[string]|true|none||none|
|»»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»»» unmap_operations|integer|true|none||none|
|»»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»»»» rd_operations|integer|true|none||none|
|»»»»»»» account_failed|boolean|true|none||none|
|»»»»»»» wr_merged|integer|true|none||none|
|»»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»»» flush_operations|integer|true|none||none|
|»»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»»» rd_bytes|integer|true|none||none|
|»»»»»» ide2|object|true|none||none|
|»»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»»» rd_merged|integer|true|none||none|
|»»»»»»» account_invalid|boolean|true|none||none|
|»»»»»»» wr_operations|integer|true|none||none|
|»»»»»»» unmap_operations|integer|true|none||none|
|»»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»»» timed_stats|[string]|true|none||none|
|»»»»»»» wr_bytes|integer|true|none||none|
|»»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»»» unmap_merged|integer|true|none||none|
|»»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»»» wr_merged|integer|true|none||none|
|»»»»»»» account_failed|boolean|true|none||none|
|»»»»»»» rd_operations|integer|true|none||none|
|»»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»»» rd_bytes|integer|true|none||none|
|»»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»»» flush_operations|integer|true|none||none|
|»»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»» scsi0|object|true|none||none|
|»»»»»»» unmap_merged|integer|true|none||none|
|»»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»»» wr_bytes|integer|true|none||none|
|»»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»»» timed_stats|[string]|true|none||none|
|»»»»»»» unmap_operations|integer|true|none||none|
|»»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»»» account_invalid|boolean|true|none||none|
|»»»»»»» wr_operations|integer|true|none||none|
|»»»»»»» rd_merged|integer|true|none||none|
|»»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»»» flush_operations|integer|true|none||none|
|»»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»»» rd_bytes|integer|true|none||none|
|»»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»»» rd_operations|integer|true|none||none|
|»»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»»» wr_merged|integer|true|none||none|
|»»»»»»» account_failed|boolean|true|none||none|
|»»»»» ballooninfo|object|true|none||none|
|»»»»»» total_mem|integer|true|none||none|
|»»»»»» free_mem|integer|true|none||none|
|»»»»»» last_update|integer|true|none||none|
|»»»»»» major_page_faults|integer|true|none||none|
|»»»»»» minor_page_faults|integer|true|none||none|
|»»»»»» actual|integer|true|none||none|
|»»»»»» max_mem|integer|true|none||none|
|»»»»»» mem_swapped_out|integer|true|none||none|
|»»»»»» mem_swapped_in|integer|true|none||none|
|»»»»» uptime|integer|true|none||none|
|»»»»» vmid|integer|true|none||none|
|»»»»» netin|integer|true|none||none|
|»»»»» cpus|integer|true|none||none|
|»»»»» maxmem|integer|true|none||none|
|»»»»» netout|integer|true|none||none|
|»»»»» ha|object|true|none||none|
|»»»»»» managed|integer|true|none||none|
|»»»»» balloon|integer|true|none||none|
|»»»»» running-machine|string|true|none||none|
|»»» rrddata|null|false|none||none|

## GET 带参数分页获取实例信息

GET /{adminPath}/getVmByParam

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|param|query|string| 否 |none|
|value|query|string| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取虚拟机主机信息

GET /{adminPath}/getVmHostInfo

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|hostId|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "vmhost": {
      "id": 25,
      "nodeid": 1,
      "vmid": 103,
      "name": "VM-01",
      "sockets": 1,
      "cores": 2,
      "threads": 2,
      "devirtualization": false,
      "kvm": true,
      "cpuModel": null,
      "modelGroup": null,
      "cpu": "host",
      "cpuUnits": 1024,
      "args": null,
      "arch": "x86_64",
      "acpi": 1,
      "memory": 1024,
      "swap": null,
      "agent": 1,
      "ide0": null,
      "ide2": "local-lvm:cloudinit",
      "net0": "virtio,bridge=vmbr0",
      "net1": null,
      "os": "CentOS-8-Stream-x64.qcow2",
      "osType": "linux",
      "iso": null,
      "template": null,
      "onBoot": 1,
      "bandwidth": 100,
      "storage": "local-lvm",
      "systemDiskSize": 40,
      "dataDisk": {
        "1": 40
      },
      "bridge": "vmbr0",
      "ipConfig": {
        "1": "ip=192.168.36.1/24,gw=192.168.36.2"
      },
      "nested": 0,
      "task": {
        "1692783654292": 135,
        "1692783654310": 136,
        "1692783693695": 137,
        "1692783694983": 138,
        "1692783695855": 139,
        "1692783696541": 140
      },
      "status": 0,
      "createTime": 1692783654293,
      "expirationTime": 2008143654293
    },
    "current": {
      "data": {
        "netin": 146336902,
        "vmid": 103,
        "running-machine": "pc-i440fx-6.1+pve0",
        "balloon": 1073741824,
        "ha": {
          "managed": 0
        },
        "maxmem": 1073741824,
        "netout": 2060997,
        "cpus": 2,
        "proxmox-support": {
          "pbs-library-version": "1.2.0 (6e555bc73a7dcfb4d0b47355b958afd101ad27b5)",
          "pbs-dirty-bitmap-migration": true,
          "pbs-dirty-bitmap": true,
          "query-bitmap-info": true,
          "pbs-masterkey": true,
          "pbs-dirty-bitmap-savevm": true
        },
        "diskread": 857323698,
        "status": "running",
        "pid": 41319,
        "disk": 0,
        "mem": 555388928,
        "running-qemu": "6.1.0",
        "name": "VM-01",
        "uptime": 67976,
        "ballooninfo": {
          "minor_page_faults": 14591362,
          "max_mem": 1073741824,
          "actual": 1073741824,
          "mem_swapped_out": 168591360,
          "mem_swapped_in": 44277760,
          "total_mem": 807714816,
          "free_mem": 252325888,
          "last_update": 1693026679,
          "major_page_faults": 6841
        },
        "blockstat": {
          "scsi1": {
            "unmap_merged": 0,
            "rd_total_time_ns": 119191813,
            "wr_bytes": 0,
            "failed_flush_operations": 0,
            "failed_unmap_operations": 0,
            "idle_time_ns": 67955735946106,
            "timed_stats": [],
            "unmap_operations": 0,
            "invalid_wr_operations": 0,
            "wr_operations": 0,
            "account_invalid": true,
            "rd_merged": 0,
            "unmap_total_time_ns": 0,
            "unmap_bytes": 0,
            "failed_wr_operations": 0,
            "flush_operations": 0,
            "invalid_rd_operations": 0,
            "wr_total_time_ns": 0,
            "rd_bytes": 9629696,
            "invalid_unmap_operations": 0,
            "flush_total_time_ns": 0,
            "wr_highest_offset": 0,
            "failed_rd_operations": 0,
            "rd_operations": 434,
            "invalid_flush_operations": 0,
            "wr_merged": 0,
            "account_failed": true
          },
          "ide2": {
            "timed_stats": [],
            "failed_unmap_operations": 0,
            "failed_flush_operations": 0,
            "idle_time_ns": 67955653219959,
            "invalid_wr_operations": 0,
            "unmap_operations": 0,
            "unmap_merged": 0,
            "rd_total_time_ns": 31770289,
            "wr_bytes": 0,
            "unmap_total_time_ns": 0,
            "unmap_bytes": 0,
            "wr_operations": 0,
            "account_invalid": true,
            "rd_merged": 0,
            "invalid_rd_operations": 0,
            "wr_total_time_ns": 0,
            "rd_bytes": 278706,
            "failed_wr_operations": 0,
            "flush_operations": 0,
            "rd_operations": 80,
            "wr_merged": 0,
            "account_failed": true,
            "invalid_flush_operations": 0,
            "invalid_unmap_operations": 0,
            "flush_total_time_ns": 0,
            "failed_rd_operations": 0,
            "wr_highest_offset": 0
          },
          "scsi0": {
            "rd_bytes": 847415296,
            "wr_total_time_ns": 28348412537,
            "invalid_rd_operations": 0,
            "flush_operations": 4706,
            "failed_wr_operations": 0,
            "invalid_flush_operations": 0,
            "account_failed": true,
            "wr_merged": 0,
            "rd_operations": 23603,
            "wr_highest_offset": 42018529280,
            "failed_rd_operations": 0,
            "invalid_unmap_operations": 0,
            "flush_total_time_ns": 2765105580,
            "unmap_operations": 0,
            "invalid_wr_operations": 0,
            "failed_unmap_operations": 0,
            "failed_flush_operations": 0,
            "idle_time_ns": 29119584508,
            "timed_stats": [],
            "wr_bytes": 553765888,
            "rd_total_time_ns": 23389570377,
            "unmap_merged": 0,
            "unmap_bytes": 0,
            "unmap_total_time_ns": 0,
            "rd_merged": 0,
            "wr_operations": 13484,
            "account_invalid": true
          }
        },
        "diskwrite": 553765888,
        "freemem": 252325888,
        "qmpstatus": "running",
        "maxdisk": 42949672960,
        "nics": {
          "tap103i0": {
            "netout": 2060997,
            "netin": 146336902
          }
        },
        "cpu": 0.0375891686123617,
        "agent": 1
      }
    },
    "rrddata": {
      "data": [
        {
          "diskwrite": 2285.22666666667,
          "time": 1693022520,
          "netin": 210.256666666667,
          "diskread": 0,
          "mem": 553779814.4,
          "netout": 5.2,
          "maxmem": 1073741824,
          "cpu": 0.0458164359850216,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "diskwrite": 1285.97333333333,
          "time": 1693022580,
          "diskread": 0,
          "netin": 155.26,
          "maxmem": 1073741824,
          "netout": 3.7,
          "mem": 555310694.4,
          "cpu": 0.0453278605314792,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2
        },
        {
          "cpu": 0.0448897799706862,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "mem": 555433369.6,
          "netout": 4.4,
          "maxmem": 1073741824,
          "netin": 160.446666666667,
          "diskread": 0,
          "diskwrite": 1826.13333333333,
          "time": 1693022640
        },
        {
          "maxmem": 1073741824,
          "netout": 5.9,
          "mem": 555385309.866667,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.045415511457912,
          "time": 1693022700,
          "diskwrite": 1288.53333333333,
          "diskread": 0,
          "netin": 201.506666666667
        },
        {
          "diskread": 0,
          "netin": 248.526666666667,
          "diskwrite": 988.16,
          "time": 1693022760,
          "cpu": 0.0471565094845228,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "maxmem": 1073741824,
          "netout": 32.3,
          "mem": 555276219.733333
        },
        {
          "cpu": 0.0478185952007954,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 553325636.266667,
          "netout": 2.35,
          "maxmem": 1073741824,
          "netin": 143.35,
          "diskread": 0,
          "diskwrite": 1041.06666666667,
          "time": 1693022820
        },
        {
          "diskwrite": 1343.14666666667,
          "time": 1693022880,
          "netin": 163.483333333333,
          "diskread": 0,
          "mem": 553349734.4,
          "maxmem": 1073741824,
          "netout": 1.35,
          "cpu": 0.0421807382775492,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "maxmem": 1073741824,
          "netout": 5.38,
          "mem": 555073945.6,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0473693362823947,
          "time": 1693022940,
          "diskwrite": 1293.65333333333,
          "diskread": 0,
          "netin": 144.15
        },
        {
          "diskwrite": 1343.14666666667,
          "time": 1693023000,
          "diskread": 0,
          "netin": 280.555,
          "netout": 26.72,
          "maxmem": 1073741824,
          "mem": 555158459.733333,
          "cpu": 0.048439203953755,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2
        },
        {
          "cpu": 0.0474418857419659,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "netout": 31.65,
          "maxmem": 1073741824,
          "mem": 553412334.933333,
          "diskread": 0,
          "netin": 218.545,
          "diskwrite": 1609.38666666667,
          "time": 1693023060
        },
        {
          "diskread": 0,
          "netin": 354.681666666667,
          "diskwrite": 711.68,
          "time": 1693023120,
          "cpu": 0.0485055899978731,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "netout": 1.5,
          "maxmem": 1073741824,
          "mem": 553024648.533333
        },
        {
          "mem": 555163101.866667,
          "maxmem": 1073741824,
          "netout": 2.2,
          "cpu": 0.0476665704732284,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "diskwrite": 1456.64,
          "time": 1693023180,
          "netin": 347.58,
          "diskread": 0
        },
        {
          "netout": 2.2,
          "maxmem": 1073741824,
          "mem": 555235601.066667,
          "cpu": 0.0478193672498925,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "diskwrite": 727.893333333333,
          "time": 1693023240,
          "diskread": 0,
          "netin": 232.021666666667
        },
        {
          "time": 1693023300,
          "diskwrite": 1297.06666666667,
          "netin": 164.9,
          "diskread": 0,
          "mem": 555315131.733333,
          "maxmem": 1073741824,
          "netout": 4.4,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0484923430644485
        },
        {
          "mem": 555363737.6,
          "netout": 2.2,
          "maxmem": 1073741824,
          "cpu": 0.046744671039322,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "diskwrite": 981.333333333333,
          "time": 1693023360,
          "netin": 148.72,
          "diskread": 0
        },
        {
          "netin": 230.856666666667,
          "diskread": 0,
          "diskwrite": 665.6,
          "time": 1693023420,
          "cpu": 0.0480634605390481,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 555351517.866667,
          "maxmem": 1073741824,
          "netout": 29.4666666666667
        },
        {
          "netin": 138.94,
          "diskread": 0,
          "diskwrite": 748.373333333333,
          "time": 1693023480,
          "cpu": 0.0471353660049127,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 554721962.666667,
          "netout": 3.7,
          "maxmem": 1073741824
        },
        {
          "diskwrite": 2636.8,
          "time": 1693023540,
          "netin": 306.296666666667,
          "diskread": 0,
          "mem": 554887372.8,
          "netout": 27.5833333333333,
          "maxmem": 1073741824,
          "cpu": 0.0453493358834041,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "netin": 255.203333333333,
          "diskread": 0,
          "diskwrite": 2559.14666666667,
          "time": 1693023600,
          "cpu": 0.0500417896848725,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 553428855.466667,
          "maxmem": 1073741824,
          "netout": 27.6833333333333
        },
        {
          "cpu": 0.0413691084723431,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 554723601.066667,
          "maxmem": 1073741824,
          "netout": 2.61,
          "netin": 171.616666666667,
          "diskread": 0,
          "diskwrite": 1440.42666666667,
          "time": 1693023660
        },
        {
          "mem": 555187131.733333,
          "maxmem": 1073741824,
          "netout": 2.27,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0432250145389081,
          "time": 1693023720,
          "diskwrite": 712.533333333333,
          "netin": 143.2,
          "diskread": 0
        },
        {
          "time": 1693023780,
          "diskwrite": 657.066666666667,
          "netin": 236.605,
          "diskread": 0,
          "mem": 555137297.066667,
          "maxmem": 1073741824,
          "netout": 28.8866666666667,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0409425923614328
        },
        {
          "cpu": 0.046357333217842,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "netout": 1.5,
          "maxmem": 1073741824,
          "mem": 554252765.866667,
          "diskread": 0,
          "netin": 355.711666666667,
          "diskwrite": 3612.16,
          "time": 1693023840
        },
        {
          "mem": 554814395.733333,
          "netout": 2.2,
          "maxmem": 1073741824,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0405047393636851,
          "time": 1693023900,
          "diskwrite": 2895.36,
          "netin": 165.44,
          "diskread": 0
        },
        {
          "diskwrite": 2148.69333333333,
          "time": 1693023960,
          "netin": 370.106666666667,
          "diskread": 0,
          "mem": 555348923.733333,
          "maxmem": 1073741824,
          "netout": 3.7,
          "cpu": 0.0387493845740037,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0
        },
        {
          "diskread": 0,
          "netin": 150.166666666667,
          "time": 1693024020,
          "diskwrite": 426.666666666667,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.039852378390849,
          "maxmem": 1073741824,
          "netout": 2.2,
          "mem": 555310284.8
        },
        {
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.0388240416560619,
          "netout": 0,
          "maxmem": 1073741824,
          "mem": 555404083.2,
          "diskread": 0,
          "netin": 155.066666666667,
          "time": 1693024080,
          "diskwrite": 665.6
        },
        {
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.0400045515645912,
          "maxmem": 1073741824,
          "netout": 2.9,
          "mem": 555459652.266667,
          "diskread": 0,
          "netin": 147.458518518519,
          "time": 1693024140,
          "diskwrite": 665.6
        },
        {
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0400031411112753,
          "mem": 555228637.866667,
          "maxmem": 1073741824,
          "netout": 56.55,
          "netin": 345.008148148148,
          "diskread": 0,
          "time": 1693024200,
          "diskwrite": 885.76
        },
        {
          "mem": 554410257.066667,
          "netout": 2.2,
          "maxmem": 1073741824,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0389357386598446,
          "time": 1693024260,
          "diskwrite": 1913.17333333333,
          "netin": 135.7,
          "diskread": 0
        },
        {
          "diskread": 0,
          "netin": 182.416666666667,
          "time": 1693024320,
          "diskwrite": 665.6,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0419646509383975,
          "netout": 2.2,
          "maxmem": 1073741824,
          "mem": 555150131.2
        },
        {
          "diskread": 0,
          "netin": 183.466666666667,
          "diskwrite": 742.4,
          "time": 1693024380,
          "cpu": 0.0430143085347611,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "netout": 0,
          "maxmem": 1073741824,
          "mem": 555151223.466667
        },
        {
          "cpu": 0.0442551959449365,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 555070259.2,
          "maxmem": 1073741824,
          "netout": 2.76,
          "netin": 173.966666666667,
          "diskread": 0,
          "diskwrite": 810.666666666667,
          "time": 1693024440
        },
        {
          "mem": 554818082.133333,
          "maxmem": 1073741824,
          "netout": 31.1066666666667,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0412523639282956,
          "time": 1693024500,
          "diskwrite": 612.693333333333,
          "netin": 280.433333333333,
          "diskread": 546.133333333333
        },
        {
          "netin": 166.433333333333,
          "diskread": 0,
          "time": 1693024560,
          "diskwrite": 1537.70666666667,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0407245596579316,
          "mem": 553211084.8,
          "netout": 0,
          "maxmem": 1073741824
        },
        {
          "diskwrite": 1102.50666666667,
          "time": 1693024620,
          "netin": 146.933333333333,
          "diskread": 273.066666666667,
          "mem": 554442888.533333,
          "netout": 0,
          "maxmem": 1073741824,
          "cpu": 0.0429865594931745,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0370581575804948,
          "netout": 0,
          "maxmem": 1073741824,
          "mem": 555210888.533333,
          "diskread": 0,
          "netin": 157.866666666667,
          "time": 1693024680,
          "diskwrite": 1141.76
        },
        {
          "netin": 144.1,
          "diskread": 0,
          "time": 1693024740,
          "diskwrite": 384,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0323165862194575,
          "mem": 554141559.466667,
          "maxmem": 1073741824,
          "netout": 2.9
        },
        {
          "time": 1693024800,
          "diskwrite": 680.96,
          "diskread": 0,
          "netin": 267.483333333333,
          "maxmem": 1073741824,
          "netout": 30.2833333333333,
          "mem": 554852488.533333,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.0348589283450581
        },
        {
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0339460651136314,
          "netout": 45.7,
          "maxmem": 1073741824,
          "mem": 554350455.466667,
          "diskread": 0,
          "netin": 244.6,
          "time": 1693024860,
          "diskwrite": 1597.44
        },
        {
          "time": 1693024920,
          "diskwrite": 2295.46666666667,
          "diskread": 0,
          "netin": 152.273333333333,
          "netout": 6.1,
          "maxmem": 1073741824,
          "mem": 554955571.2,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0377455720124001
        },
        {
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.036714252414379,
          "mem": 554308130.133333,
          "maxmem": 1073741824,
          "netout": 5.2,
          "netin": 152.21,
          "diskread": 0,
          "time": 1693024980,
          "diskwrite": 554.666666666667
        },
        {
          "mem": 554648371.2,
          "netout": 5.9,
          "maxmem": 1073741824,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0341102835666168,
          "time": 1693025040,
          "diskwrite": 1297.06666666667,
          "netin": 153.666666666667,
          "diskread": 0
        },
        {
          "time": 1693025100,
          "diskwrite": 957.44,
          "netin": 143.12,
          "diskread": 0,
          "mem": 554138146.133333,
          "netout": 5.2,
          "maxmem": 1073741824,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0352419251933335
        },
        {
          "mem": 555374455.466667,
          "maxmem": 1073741824,
          "netout": 3.7,
          "cpu": 0.0349473480982196,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "diskwrite": 2123.09333333333,
          "time": 1693025160,
          "netin": 152.423333333333,
          "diskread": 0
        },
        {
          "maxmem": 1073741824,
          "netout": 16.85,
          "mem": 554922666.666667,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0353311879818946,
          "time": 1693025220,
          "diskwrite": 384,
          "diskread": 0,
          "netin": 141.403333333333
        },
        {
          "diskwrite": 827.733333333333,
          "time": 1693025280,
          "diskread": 0,
          "netin": 164.1,
          "maxmem": 1073741824,
          "netout": 4.3,
          "mem": 553262011.733333,
          "cpu": 0.0356942217397522,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2
        },
        {
          "cpu": 0.0338922810227853,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "maxmem": 1073741824,
          "netout": 4.76,
          "mem": 553800908.8,
          "diskread": 0,
          "netin": 146.87,
          "diskwrite": 1211.73333333333,
          "time": 1693025340
        },
        {
          "diskwrite": 1196.37333333333,
          "time": 1693025400,
          "netin": 258.35,
          "diskread": 0,
          "mem": 554803336.533333,
          "maxmem": 1073741824,
          "netout": 27.7833333333333,
          "cpu": 0.0362476716602887,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "diskwrite": 2566.82666666667,
          "time": 1693025460,
          "diskread": 0,
          "netin": 157.14,
          "netout": 3.14,
          "maxmem": 1073741824,
          "mem": 553999155.2,
          "cpu": 0.0349489378555488,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2
        },
        {
          "diskread": 0,
          "netin": 143.926666666667,
          "time": 1693025520,
          "diskwrite": 819.2,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "cpu": 0.0365218946208515,
          "netout": 6.2,
          "maxmem": 1073741824,
          "mem": 555577890.133333
        },
        {
          "cpu": 0.0356105696239801,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "maxmem": 1073741824,
          "netout": 33.15,
          "mem": 555254306.133333,
          "diskread": 0,
          "netin": 231.816666666667,
          "diskwrite": 1228.8,
          "time": 1693025580
        },
        {
          "maxmem": 1073741824,
          "netout": 6.7,
          "mem": 553456844.8,
          "cpu": 0.0371802753379984,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "diskwrite": 981.333333333333,
          "time": 1693025640,
          "diskread": 0,
          "netin": 148.366666666667
        },
        {
          "netin": 153.666666666667,
          "diskread": 0,
          "diskwrite": 1585.49333333333,
          "time": 1693025700,
          "cpu": 0.0353806501772128,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "mem": 553966796.8,
          "maxmem": 1073741824,
          "netout": 5.46
        },
        {
          "diskwrite": 1605.97333333333,
          "time": 1693025760,
          "diskread": 0,
          "netin": 146.3,
          "maxmem": 1073741824,
          "netout": 6.84,
          "mem": 555296221.866667,
          "cpu": 0.036130295559432,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2
        },
        {
          "diskwrite": 1365.33333333333,
          "time": 1693025820,
          "netin": 151.966666666667,
          "diskread": 0,
          "mem": 555287756.8,
          "netout": 4,
          "maxmem": 1073741824,
          "cpu": 0.0324734174276538,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960
        },
        {
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.032250381702533,
          "mem": 555305096.533333,
          "maxmem": 1073741824,
          "netout": 5.2,
          "netin": 145.113333333333,
          "diskread": 0,
          "time": 1693025880,
          "diskwrite": 665.6
        },
        {
          "diskread": 0,
          "netin": 235.246666666667,
          "diskwrite": 665.6,
          "time": 1693025940,
          "cpu": 0.0375887094655561,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "netout": 34.0666666666667,
          "maxmem": 1073741824,
          "mem": 555351927.466667
        },
        {
          "netin": 259.293333333333,
          "diskread": 0,
          "time": 1693026000,
          "diskwrite": 1003.52,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0325173051687561,
          "mem": 553964748.8,
          "maxmem": 1073741824,
          "netout": 32.8833333333333
        },
        {
          "mem": 553512823.466667,
          "netout": 5.46,
          "maxmem": 1073741824,
          "cpu": 0.0311690442375919,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "diskwrite": 1879.04,
          "time": 1693026060,
          "netin": 161.85,
          "diskread": 0
        },
        {
          "time": 1693026120,
          "diskwrite": 2257.92,
          "diskread": 0,
          "netin": 152.216666666667,
          "maxmem": 1073741824,
          "netout": 5.9,
          "mem": 555265228.8,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.0326057963079776
        },
        {
          "diskwrite": 1225.38666666667,
          "time": 1693026180,
          "netin": 138.88,
          "diskread": 0,
          "mem": 555451187.2,
          "maxmem": 1073741824,
          "netout": 3.84,
          "cpu": 0.0367577129267108,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0
        },
        {
          "cpu": 0.0325471280329204,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "netout": 5.2,
          "maxmem": 1073741824,
          "mem": 555465932.8,
          "diskread": 0,
          "netin": 158.233333333333,
          "diskwrite": 1505.28,
          "time": 1693026240
        },
        {
          "maxmem": 1073741824,
          "netout": 17.85,
          "mem": 555429614.933333,
          "cpu": 0.0352429042969101,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "diskwrite": 1303.89333333333,
          "time": 1693026300,
          "diskread": 0,
          "netin": 151.666666666667
        },
        {
          "diskread": 0,
          "netin": 155.166666666667,
          "time": 1693026360,
          "diskwrite": 1559.89333333333,
          "disk": 0,
          "maxdisk": 42949672960,
          "maxcpu": 2,
          "cpu": 0.034540772565946,
          "netout": 6.8,
          "maxmem": 1073741824,
          "mem": 553808691.2
        },
        {
          "time": 1693026420,
          "diskwrite": 1365.33333333333,
          "netin": 150.4,
          "diskread": 0,
          "mem": 553397043.2,
          "maxmem": 1073741824,
          "netout": 5.2,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "cpu": 0.0361311423441499
        },
        {
          "netin": 153.866666666667,
          "diskread": 0,
          "time": 1693026480,
          "diskwrite": 989.866666666667,
          "maxcpu": 2,
          "maxdisk": 42949672960,
          "disk": 0,
          "cpu": 0.0353018852664372,
          "mem": 555234918.4,
          "maxmem": 1073741824,
          "netout": 4.9
        },
        {
          "netout": 5.2,
          "maxmem": 1073741824,
          "mem": 553180228.266667,
          "cpu": 0.0362618697208855,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "diskwrite": 1203.2,
          "time": 1693026540,
          "diskread": 0,
          "netin": 143.12
        },
        {
          "diskread": 0,
          "netin": 346.613333333333,
          "diskwrite": 1201.49333333333,
          "time": 1693026600,
          "cpu": 0.0328663245058028,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 2,
          "maxmem": 1073741824,
          "netout": 58.35,
          "mem": 555208157.866667
        },
        {
          "cpu": 0.0334612498405188,
          "maxcpu": 2,
          "disk": 0,
          "maxdisk": 42949672960,
          "mem": 553641028.266667,
          "maxmem": 1073741824,
          "netout": 4.86666666666667,
          "netin": 143.12,
          "diskread": 0,
          "diskwrite": 1563.30666666667,
          "time": 1693026660
        }
      ]
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» vmhost|object|true|none||none|
|»»» id|integer|true|none||none|
|»»» nodeid|integer|true|none||none|
|»»» vmid|integer|true|none||none|
|»»» name|string|true|none||none|
|»»» sockets|integer|true|none||none|
|»»» cores|integer|true|none||none|
|»»» threads|integer|true|none||none|
|»»» devirtualization|boolean|true|none||none|
|»»» kvm|boolean|true|none||none|
|»»» cpuModel|null|true|none||none|
|»»» modelGroup|null|true|none||none|
|»»» cpu|string|true|none||none|
|»»» cpuUnits|integer|true|none||none|
|»»» args|null|true|none||none|
|»»» arch|string|true|none||none|
|»»» acpi|integer|true|none||none|
|»»» memory|integer|true|none||none|
|»»» swap|null|true|none||none|
|»»» agent|integer|true|none||none|
|»»» ide0|null|true|none||none|
|»»» ide2|string|true|none||none|
|»»» net0|string|true|none||none|
|»»» net1|null|true|none||none|
|»»» os|string|true|none||none|
|»»» osType|string|true|none||none|
|»»» iso|null|true|none||none|
|»»» template|null|true|none||none|
|»»» onBoot|integer|true|none||none|
|»»» bandwidth|integer|true|none||none|
|»»» storage|string|true|none||none|
|»»» systemDiskSize|integer|true|none||none|
|»»» dataDisk|object|true|none||none|
|»»»» 1|integer|true|none||none|
|»»» bridge|string|true|none||none|
|»»» ipConfig|object|true|none||none|
|»»»» 1|string|true|none||none|
|»»» nested|integer|true|none||none|
|»»» task|object|true|none||none|
|»»»» 1692783654292|integer|true|none||none|
|»»»» 1692783654310|integer|true|none||none|
|»»»» 1692783693695|integer|true|none||none|
|»»»» 1692783694983|integer|true|none||none|
|»»»» 1692783695855|integer|true|none||none|
|»»»» 1692783696541|integer|true|none||none|
|»»» status|integer|true|none||none|
|»»» createTime|integer|true|none||none|
|»»» expirationTime|integer|true|none||none|
|»» current|object|true|none||none|
|»»» data|object|true|none||none|
|»»»» netin|integer|true|none||none|
|»»»» vmid|integer|true|none||none|
|»»»» running-machine|string|true|none||none|
|»»»» balloon|integer|true|none||none|
|»»»» ha|object|true|none||none|
|»»»»» managed|integer|true|none||none|
|»»»» maxmem|integer|true|none||none|
|»»»» netout|integer|true|none||none|
|»»»» cpus|integer|true|none||none|
|»»»» proxmox-support|object|true|none||none|
|»»»»» pbs-library-version|string|true|none||none|
|»»»»» pbs-dirty-bitmap-migration|boolean|true|none||none|
|»»»»» pbs-dirty-bitmap|boolean|true|none||none|
|»»»»» query-bitmap-info|boolean|true|none||none|
|»»»»» pbs-masterkey|boolean|true|none||none|
|»»»»» pbs-dirty-bitmap-savevm|boolean|true|none||none|
|»»»» diskread|integer|true|none||none|
|»»»» status|string|true|none||none|
|»»»» pid|integer|true|none||none|
|»»»» disk|integer|true|none||none|
|»»»» mem|integer|true|none||none|
|»»»» running-qemu|string|true|none||none|
|»»»» name|string|true|none||none|
|»»»» uptime|integer|true|none||none|
|»»»» ballooninfo|object|true|none||none|
|»»»»» minor_page_faults|integer|true|none||none|
|»»»»» max_mem|integer|true|none||none|
|»»»»» actual|integer|true|none||none|
|»»»»» mem_swapped_out|integer|true|none||none|
|»»»»» mem_swapped_in|integer|true|none||none|
|»»»»» total_mem|integer|true|none||none|
|»»»»» free_mem|integer|true|none||none|
|»»»»» last_update|integer|true|none||none|
|»»»»» major_page_faults|integer|true|none||none|
|»»»» blockstat|object|true|none||none|
|»»»»» scsi1|object|true|none||none|
|»»»»»» unmap_merged|integer|true|none||none|
|»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»» wr_bytes|integer|true|none||none|
|»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»» timed_stats|[string]|true|none||none|
|»»»»»» unmap_operations|integer|true|none||none|
|»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»» wr_operations|integer|true|none||none|
|»»»»»» account_invalid|boolean|true|none||none|
|»»»»»» rd_merged|integer|true|none||none|
|»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»» flush_operations|integer|true|none||none|
|»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»» rd_bytes|integer|true|none||none|
|»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»» rd_operations|integer|true|none||none|
|»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»» wr_merged|integer|true|none||none|
|»»»»»» account_failed|boolean|true|none||none|
|»»»»» ide2|object|true|none||none|
|»»»»»» timed_stats|[string]|true|none||none|
|»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»» unmap_operations|integer|true|none||none|
|»»»»»» unmap_merged|integer|true|none||none|
|»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»» wr_bytes|integer|true|none||none|
|»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»» wr_operations|integer|true|none||none|
|»»»»»» account_invalid|boolean|true|none||none|
|»»»»»» rd_merged|integer|true|none||none|
|»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»» rd_bytes|integer|true|none||none|
|»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»» flush_operations|integer|true|none||none|
|»»»»»» rd_operations|integer|true|none||none|
|»»»»»» wr_merged|integer|true|none||none|
|»»»»»» account_failed|boolean|true|none||none|
|»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»» scsi0|object|true|none||none|
|»»»»»» rd_bytes|integer|true|none||none|
|»»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»»» flush_operations|integer|true|none||none|
|»»»»»» failed_wr_operations|integer|true|none||none|
|»»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»»» account_failed|boolean|true|none||none|
|»»»»»» wr_merged|integer|true|none||none|
|»»»»»» rd_operations|integer|true|none||none|
|»»»»»» wr_highest_offset|integer|true|none||none|
|»»»»»» failed_rd_operations|integer|true|none||none|
|»»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»»» unmap_operations|integer|true|none||none|
|»»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»»» failed_flush_operations|integer|true|none||none|
|»»»»»» idle_time_ns|integer|true|none||none|
|»»»»»» timed_stats|[string]|true|none||none|
|»»»»»» wr_bytes|integer|true|none||none|
|»»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»»» unmap_merged|integer|true|none||none|
|»»»»»» unmap_bytes|integer|true|none||none|
|»»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»»» rd_merged|integer|true|none||none|
|»»»»»» wr_operations|integer|true|none||none|
|»»»»»» account_invalid|boolean|true|none||none|
|»»»» diskwrite|integer|true|none||none|
|»»»» freemem|integer|true|none||none|
|»»»» qmpstatus|string|true|none||none|
|»»»» maxdisk|integer|true|none||none|
|»»»» nics|object|true|none||none|
|»»»»» tap103i0|object|true|none||none|
|»»»»»» netout|integer|true|none||none|
|»»»»»» netin|integer|true|none||none|
|»»»» cpu|number|true|none||none|
|»»»» agent|integer|true|none||none|
|»» rrddata|object|true|none||none|
|»»» data|[object]|true|none||none|
|»»»» diskwrite|number|true|none||none|
|»»»» time|integer|true|none||none|
|»»»» netin|number|true|none||none|
|»»»» diskread|integer|true|none||none|
|»»»» mem|number|true|none||none|
|»»»» netout|number|true|none||none|
|»»»» maxmem|integer|true|none||none|
|»»»» cpu|number|true|none||none|
|»»»» maxcpu|integer|true|none||none|
|»»»» disk|integer|true|none||none|
|»»»» maxdisk|integer|true|none||none|

## GET 获取虚拟机历史负载

GET /{adminPath}/getVmHostRrdData

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|hostId|query|integer| 否 |虚拟机id|
|timeframe|query|string| 否 |时间范围[hour,day,week,month,year]|
|cf|query|string| 否 |数据类型[AVERAGE,MAX]|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": [
    {
      "mem": 553262011.733333,
      "maxmem": 1073741824,
      "netout": 4.3,
      "cpu": 0.0356942217397522,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "diskwrite": 827.733333333333,
      "time": 1693025280,
      "netin": 164.1,
      "diskread": 0
    },
    {
      "time": 1693025340,
      "diskwrite": 1211.73333333333,
      "netin": 146.87,
      "diskread": 0,
      "mem": 553800908.8,
      "netout": 4.76,
      "maxmem": 1073741824,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0338922810227853
    },
    {
      "mem": 554803336.533333,
      "maxmem": 1073741824,
      "netout": 27.7833333333333,
      "cpu": 0.0362476716602887,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "diskwrite": 1196.37333333333,
      "time": 1693025400,
      "netin": 258.35,
      "diskread": 0
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0349489378555488,
      "mem": 553999155.2,
      "maxmem": 1073741824,
      "netout": 3.14,
      "netin": 157.14,
      "diskread": 0,
      "time": 1693025460,
      "diskwrite": 2566.82666666667
    },
    {
      "diskwrite": 819.2,
      "time": 1693025520,
      "diskread": 0,
      "netin": 143.926666666667,
      "netout": 6.2,
      "maxmem": 1073741824,
      "mem": 555577890.133333,
      "cpu": 0.0365218946208515,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2
    },
    {
      "mem": 555254306.133333,
      "netout": 33.15,
      "maxmem": 1073741824,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0356105696239801,
      "time": 1693025580,
      "diskwrite": 1228.8,
      "netin": 231.816666666667,
      "diskread": 0
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0371802753379984,
      "mem": 553456844.8,
      "maxmem": 1073741824,
      "netout": 6.7,
      "netin": 148.366666666667,
      "diskread": 0,
      "time": 1693025640,
      "diskwrite": 981.333333333333
    },
    {
      "netin": 153.666666666667,
      "diskread": 0,
      "diskwrite": 1585.49333333333,
      "time": 1693025700,
      "cpu": 0.0353806501772128,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "mem": 553966796.8,
      "netout": 5.46,
      "maxmem": 1073741824
    },
    {
      "diskread": 0,
      "netin": 146.3,
      "diskwrite": 1605.97333333333,
      "time": 1693025760,
      "cpu": 0.036130295559432,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "netout": 6.84,
      "maxmem": 1073741824,
      "mem": 555296221.866667
    },
    {
      "time": 1693025820,
      "diskwrite": 1365.33333333333,
      "netin": 151.966666666667,
      "diskread": 0,
      "mem": 555287756.8,
      "netout": 4,
      "maxmem": 1073741824,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0324734174276538
    },
    {
      "diskread": 0,
      "netin": 145.113333333333,
      "time": 1693025880,
      "diskwrite": 665.6,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.032250381702533,
      "maxmem": 1073741824,
      "netout": 5.2,
      "mem": 555305096.533333
    },
    {
      "netin": 235.246666666667,
      "diskread": 0,
      "time": 1693025940,
      "diskwrite": 665.6,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0375887094655561,
      "mem": 555351927.466667,
      "maxmem": 1073741824,
      "netout": 34.0666666666667
    },
    {
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0325173051687561,
      "netout": 32.8833333333333,
      "maxmem": 1073741824,
      "mem": 553964748.8,
      "diskread": 0,
      "netin": 259.293333333333,
      "time": 1693026000,
      "diskwrite": 1003.52
    },
    {
      "maxmem": 1073741824,
      "netout": 5.46,
      "mem": 553512823.466667,
      "cpu": 0.0311690442375919,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "diskwrite": 1879.04,
      "time": 1693026060,
      "diskread": 0,
      "netin": 161.85
    },
    {
      "time": 1693026120,
      "diskwrite": 2257.92,
      "diskread": 0,
      "netin": 152.216666666667,
      "netout": 5.9,
      "maxmem": 1073741824,
      "mem": 555265228.8,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0326057963079776
    },
    {
      "cpu": 0.0367577129267108,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "netout": 3.84,
      "maxmem": 1073741824,
      "mem": 555451187.2,
      "diskread": 0,
      "netin": 138.88,
      "diskwrite": 1225.38666666667,
      "time": 1693026180
    },
    {
      "mem": 555465932.8,
      "netout": 5.2,
      "maxmem": 1073741824,
      "cpu": 0.0325471280329204,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "diskwrite": 1505.28,
      "time": 1693026240,
      "netin": 158.233333333333,
      "diskread": 0
    },
    {
      "cpu": 0.0352429042969101,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "maxmem": 1073741824,
      "netout": 17.85,
      "mem": 555429614.933333,
      "diskread": 0,
      "netin": 151.666666666667,
      "diskwrite": 1303.89333333333,
      "time": 1693026300
    },
    {
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.034540772565946,
      "mem": 553808691.2,
      "netout": 6.8,
      "maxmem": 1073741824,
      "netin": 155.166666666667,
      "diskread": 0,
      "time": 1693026360,
      "diskwrite": 1559.89333333333
    },
    {
      "netout": 5.2,
      "maxmem": 1073741824,
      "mem": 553397043.2,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "cpu": 0.0361311423441499,
      "time": 1693026420,
      "diskwrite": 1365.33333333333,
      "diskread": 0,
      "netin": 150.4
    },
    {
      "diskwrite": 989.866666666667,
      "time": 1693026480,
      "netin": 153.866666666667,
      "diskread": 0,
      "mem": 555234918.4,
      "netout": 4.9,
      "maxmem": 1073741824,
      "cpu": 0.0353018852664372,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960
    },
    {
      "diskread": 0,
      "netin": 143.12,
      "diskwrite": 1203.2,
      "time": 1693026540,
      "cpu": 0.0362618697208855,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "netout": 5.2,
      "maxmem": 1073741824,
      "mem": 553180228.266667
    },
    {
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0328663245058028,
      "mem": 555208157.866667,
      "maxmem": 1073741824,
      "netout": 58.35,
      "netin": 346.613333333333,
      "diskread": 0,
      "time": 1693026600,
      "diskwrite": 1201.49333333333
    },
    {
      "mem": 553641028.266667,
      "maxmem": 1073741824,
      "netout": 4.86666666666667,
      "cpu": 0.0334612498405188,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "diskwrite": 1563.30666666667,
      "time": 1693026660,
      "netin": 143.12,
      "diskread": 0
    },
    {
      "netout": 5.9,
      "maxmem": 1073741824,
      "mem": 555262907.733333,
      "cpu": 0.035540661173939,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "diskwrite": 836.266666666667,
      "time": 1693026720,
      "diskread": 0,
      "netin": 154.923333333333
    },
    {
      "maxmem": 1073741824,
      "netout": 4.9,
      "mem": 555320115.2,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "cpu": 0.0325156885724104,
      "time": 1693026780,
      "diskwrite": 1194.66666666667,
      "diskread": 0,
      "netin": 146.21
    },
    {
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "cpu": 0.0314702190712302,
      "netout": 4.9,
      "maxmem": 1073741824,
      "mem": 555381964.8,
      "diskread": 0,
      "netin": 151.8,
      "time": 1693026840,
      "diskwrite": 2227.2
    },
    {
      "netin": 219.996666666667,
      "diskread": 0,
      "time": 1693026900,
      "diskwrite": 885.76,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0353587366699854,
      "mem": 555496925.866667,
      "netout": 28.2666666666667,
      "maxmem": 1073741824
    },
    {
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.035169128215583,
      "netout": 3.4,
      "maxmem": 1073741824,
      "mem": 554462685.866667,
      "diskread": 0,
      "netin": 144.3,
      "time": 1693026960,
      "diskwrite": 1285.12
    },
    {
      "netin": 149.466666666667,
      "diskread": 0,
      "diskwrite": 1303.89333333333,
      "time": 1693027020,
      "cpu": 0.0307301723016114,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "mem": 553416840.533333,
      "maxmem": 1073741824,
      "netout": 1.8
    },
    {
      "maxmem": 1073741824,
      "netout": 4.4,
      "mem": 555083366.4,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0303453226955347,
      "time": 1693027080,
      "diskwrite": 926.72,
      "diskread": 0,
      "netin": 145.6
    },
    {
      "mem": 553869312,
      "maxmem": 1073741824,
      "netout": 29.7833333333333,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0317315344292921,
      "time": 1693027140,
      "diskwrite": 1087.14666666667,
      "netin": 263.5,
      "diskread": 0
    },
    {
      "cpu": 0.0311751965540391,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "maxmem": 1073741824,
      "netout": 27.5433333333333,
      "mem": 554742442.666667,
      "diskread": 0,
      "netin": 255.25,
      "diskwrite": 1751.04,
      "time": 1693027200
    },
    {
      "cpu": 0.0340954725963093,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "netout": 31.79,
      "maxmem": 1073741824,
      "mem": 555245704.533333,
      "diskread": 0,
      "netin": 231.766666666667,
      "diskwrite": 1681.06666666667,
      "time": 1693027260
    },
    {
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0357137612331302,
      "maxmem": 1073741824,
      "netout": 3.4,
      "mem": 554315229.866667,
      "diskread": 0,
      "netin": 144.3,
      "time": 1693027320,
      "diskwrite": 860.16
    },
    {
      "mem": 553249314.133333,
      "maxmem": 1073741824,
      "netout": 5.4,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0336196368315265,
      "time": 1693027380,
      "diskwrite": 1450.66666666667,
      "netin": 167.216666666667,
      "diskread": 0
    },
    {
      "diskwrite": 962.56,
      "time": 1693027440,
      "diskread": 0,
      "netin": 136.653333333333,
      "netout": 0,
      "maxmem": 1073741824,
      "mem": 554844706.133333,
      "cpu": 0.0338891987545408,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2
    },
    {
      "time": 1693027500,
      "diskwrite": 1769.81333333333,
      "netin": 741.563333333333,
      "diskread": 0,
      "mem": 555365444.266667,
      "maxmem": 1073741824,
      "netout": 3.7,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0341133307432961
    },
    {
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0331476747797071,
      "mem": 554388957.866667,
      "maxmem": 1073741824,
      "netout": 14.05,
      "netin": 169.383333333333,
      "diskread": 0,
      "time": 1693027560,
      "diskwrite": 1097.38666666667
    },
    {
      "netin": 159.72,
      "diskread": 0,
      "time": 1693027620,
      "diskwrite": 2401.28,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0341463068376501,
      "mem": 553257779.2,
      "maxmem": 1073741824,
      "netout": 5.8
    },
    {
      "cpu": 0.0347253781119409,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "mem": 554723874.133333,
      "maxmem": 1073741824,
      "netout": 0.3,
      "netin": 146.016666666667,
      "diskread": 0,
      "diskwrite": 1566.72,
      "time": 1693027680
    },
    {
      "mem": 554097732.266667,
      "maxmem": 1073741824,
      "netout": 0.7,
      "cpu": 0.0325404522863635,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "diskwrite": 1389.22666666667,
      "time": 1693027740,
      "netin": 145.63,
      "diskread": 0
    },
    {
      "diskread": 0,
      "netin": 263.716666666667,
      "time": 1693027800,
      "diskwrite": 1920,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0316473842227769,
      "netout": 28.8833333333333,
      "maxmem": 1073741824,
      "mem": 554740394.666667
    },
    {
      "netout": 4.4,
      "maxmem": 1073741824,
      "mem": 555551129.6,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0303894522097265,
      "time": 1693027860,
      "diskwrite": 1640.10666666667,
      "diskread": 0,
      "netin": 145.6
    },
    {
      "mem": 554434013.866667,
      "maxmem": 1073741824,
      "netout": 28.25,
      "cpu": 0.0310910893804443,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "diskwrite": 928.426666666667,
      "time": 1693027920,
      "netin": 227.866666666667,
      "diskread": 0
    },
    {
      "cpu": 0.0305845559207986,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "maxmem": 1073741824,
      "netout": 0,
      "mem": 553188147.2,
      "diskread": 0,
      "netin": 141.6,
      "diskwrite": 1324.37333333333,
      "time": 1693027980
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0300794923912669,
      "mem": 554543377.066667,
      "netout": 2.2,
      "maxmem": 1073741824,
      "netin": 150.166666666667,
      "diskread": 0,
      "time": 1693028040,
      "diskwrite": 928.426666666667
    },
    {
      "diskwrite": 1810.77333333333,
      "time": 1693028100,
      "netin": 143.82,
      "diskread": 0,
      "mem": 555285299.2,
      "maxmem": 1073741824,
      "netout": 4.1,
      "cpu": 0.0305285603268198,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960
    },
    {
      "cpu": 0.0300286469761978,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "maxmem": 1073741824,
      "netout": 2.5,
      "mem": 555369676.8,
      "diskread": 0,
      "netin": 151.946666666667,
      "diskwrite": 1153.70666666667,
      "time": 1693028160
    },
    {
      "mem": 555220172.8,
      "maxmem": 1073741824,
      "netout": 0,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0309338919494333,
      "time": 1693028220,
      "diskwrite": 657.066666666667,
      "netin": 145.453333333333,
      "diskread": 0
    },
    {
      "diskread": 0,
      "netin": 224.29,
      "time": 1693028280,
      "diskwrite": 781.653333333333,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "cpu": 0.0301318190780915,
      "netout": 29.45,
      "maxmem": 1073741824,
      "mem": 555427293.866667
    },
    {
      "netin": 145.983333333333,
      "diskread": 0,
      "time": 1693028340,
      "diskwrite": 1268.05333333333,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0301929903774938,
      "mem": 553310344.533333,
      "netout": 2.2,
      "maxmem": 1073741824
    },
    {
      "cpu": 0.0335507546789128,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "mem": 554319462.4,
      "netout": 29.1833333333333,
      "maxmem": 1073741824,
      "netin": 263.81,
      "diskread": 0,
      "diskwrite": 1792,
      "time": 1693028400
    },
    {
      "diskwrite": 2351.78666666667,
      "time": 1693028460,
      "diskread": 0,
      "netin": 139.796666666667,
      "maxmem": 1073741824,
      "netout": 0,
      "mem": 555462382.933333,
      "cpu": 0.0330557747543976,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2
    },
    {
      "netout": 1.76,
      "maxmem": 1073741824,
      "mem": 555597687.466667,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.0320434601077995,
      "time": 1693028520,
      "diskwrite": 657.066666666667,
      "diskread": 0,
      "netin": 142.6
    },
    {
      "netin": 157.5,
      "diskread": 0,
      "time": 1693028580,
      "diskwrite": 657.066666666667,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0350577177344099,
      "mem": 555484364.8,
      "netout": 2.9,
      "maxmem": 1073741824
    },
    {
      "cpu": 0.0348995646034907,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "mem": 555458696.533333,
      "maxmem": 1073741824,
      "netout": 29.89,
      "netin": 217.183333333333,
      "diskread": 0,
      "diskwrite": 657.066666666667,
      "time": 1693028640
    },
    {
      "netin": 147.666666666667,
      "diskread": 0,
      "time": 1693028700,
      "diskwrite": 1126.4,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0347773607721026,
      "mem": 553237981.866667,
      "netout": 0,
      "maxmem": 1073741824
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0362035931091147,
      "mem": 553876957.866667,
      "maxmem": 1073741824,
      "netout": 0,
      "netin": 140.6,
      "diskread": 0,
      "time": 1693028760,
      "diskwrite": 2193.06666666667
    },
    {
      "netin": 151.273333333333,
      "diskread": 0,
      "diskwrite": 1351.68,
      "time": 1693028820,
      "cpu": 0.0387058585583594,
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "mem": 555312332.8,
      "netout": 2.2,
      "maxmem": 1073741824
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0350159680068759,
      "mem": 555373363.2,
      "maxmem": 1073741824,
      "netout": 4.96,
      "netin": 153.01,
      "diskread": 0,
      "time": 1693028880,
      "diskwrite": 392.533333333333
    },
    {
      "diskread": 0,
      "netin": 149.146666666667,
      "time": 1693028940,
      "diskwrite": 377.173333333333,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "cpu": 0.0313840959101507,
      "maxmem": 1073741824,
      "netout": 2.34,
      "mem": 555315473.066667
    },
    {
      "time": 1693029000,
      "diskwrite": 559.786666666667,
      "diskread": 0,
      "netin": 326.533333333333,
      "maxmem": 1073741824,
      "netout": 53.45,
      "mem": 555420125.866667,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "cpu": 0.033278716184178
    },
    {
      "cpu": 0.0321073695491088,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "mem": 553701922.133333,
      "netout": 2.2,
      "maxmem": 1073741824,
      "netin": 150.166666666667,
      "diskread": 546.133333333333,
      "diskwrite": 1687.89333333333,
      "time": 1693029060
    },
    {
      "netout": 2.2,
      "maxmem": 1073741824,
      "mem": 553905698.133333,
      "cpu": 0.0334243012323069,
      "disk": 0,
      "maxdisk": 42949672960,
      "maxcpu": 2,
      "diskwrite": 986.453333333333,
      "time": 1693029120,
      "diskread": 0,
      "netin": 143.1
    },
    {
      "maxcpu": 2,
      "disk": 0,
      "maxdisk": 42949672960,
      "cpu": 0.0320751221695711,
      "mem": 555480883.2,
      "maxmem": 1073741824,
      "netout": 0,
      "netin": 148.063333333333,
      "diskread": 0,
      "time": 1693029180,
      "diskwrite": 537.6
    },
    {
      "netout": 0,
      "maxmem": 1073741824,
      "mem": 555637964.8,
      "cpu": 0.0320027721182605,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "diskwrite": 882.346666666667,
      "time": 1693029240,
      "diskread": 0,
      "netin": 150.586666666667
    },
    {
      "netin": 134.2,
      "diskread": 0,
      "time": 1693029300,
      "diskwrite": 590.506666666667,
      "maxcpu": 2,
      "maxdisk": 42949672960,
      "disk": 0,
      "cpu": 0.0321835188758289,
      "mem": 555617757.866667,
      "maxmem": 1073741824,
      "netout": 0
    },
    {
      "cpu": 0.032562370630759,
      "maxdisk": 42949672960,
      "disk": 0,
      "maxcpu": 2,
      "maxmem": 1073741824,
      "netout": 30.4166666666667,
      "mem": 555616802.133333,
      "diskread": 0,
      "netin": 236,
      "diskwrite": 1397.76,
      "time": 1693029360
    },
    {
      "time": 1693029420
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|[object]|true|none||none|
|»» mem|number|true|none||none|
|»» maxmem|integer|true|none||none|
|»» netout|number|true|none||none|
|»» cpu|number|true|none||none|
|»» maxcpu|integer|true|none||none|
|»» maxdisk|integer|true|none||none|
|»» disk|integer|true|none||none|
|»» diskwrite|number|true|none||none|
|»» time|integer|true|none||none|
|»» netin|number|true|none||none|
|»» diskread|integer|true|none||none|

## GET 获取指定虚拟机的vnc地址

GET /{adminPath}/{node}/getVnc

获取指定虚拟机的vnc地址

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|node|path|string| 是 |none|
|hostId|query|integer| 否 |可以为vmid|
|page|query|integer| 否 |页数|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "total": 1,
    "current": 1,
    "pages": 1,
    "size": 5,
    "records": [
      {
        "本地测试": "http://192.168.36.155:6080/vnc.html?path=websockify/?token=qimen&port=59000"
      }
    ]
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» total|integer|true|none||none|
|»» current|integer|true|none||none|
|»» pages|integer|true|none||none|
|»» size|integer|true|none||none|
|»» records|[object]|true|none||none|
|»»» 本地测试|string|false|none||none|

## PUT 修改指定虚拟机的VNC密码

PUT /api/v1/{node}/updateVncPassword

修改指定虚拟机的VNC密码

> Body 请求参数

```json
{
  "hostId": 0,
  "password": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|node|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |none|
|» password|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 以降序排列获取虚拟机分页列表

GET /{adminPath}/getVmByPageOrderByCreateTime

获取虚拟机分页列表,根据创建时间降序排列

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取实例总数

GET /{adminPath}/getVmCount

获取虚拟机总数

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页获取指定状态的虚拟机列表

GET /{adminPath}/getVmByStatus

分页获取指定状态的虚拟机列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|status|query|integer| 否 |0为开机，详细查看电源管理接口|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取指定状态的虚拟机总数

GET /{adminPath}/getVmCountByStatus

获取指定状态的虚拟机总数

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|status|query|integer| 否 |0为开机，详细查看电源管理接口|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/实例管理/配置模板管理

## POST 增加配置模板

POST /{adminPath}/addConfiguretemplate

增加配置模板

> Body 请求参数

```json
{
  "sockets": 1,
  "cores": 2,
  "threads": 2,
  "nested": false,
  "cpu": "host",
  "arch": "x86_64",
  "memory": 1024,
  "storage": "auto",
  "systemDiskSize": 40,
  "bandwidth": 100,
  "onBoot": 1
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» configureTemplateId|body|integer¦null| 否 |配置模板ID|
|» sockets|body|integer¦null| 否 |插槽数，默认为1|
|» cores|body|integer¦null| 否 |核心，默认1|
|» threads|body|integer¦null| 否 |线程数，默认1|
|» devirtualization|body|boolean¦null| 否 |是否去虚拟化，默认false|
|» kvm|body|boolean¦null| 否 |是否开启kvm虚拟化，默认开启|
|» cpuModel|body|integer¦null| 否 |cpu信息模型ID|
|» modelGroup|body|integer¦null| 否 |组合模型ID，优先级大于cpuModel|
|» nested|body|boolean¦null| 否 |是否开启嵌套虚拟化，默认关闭|
|» cpu|body|string¦null| 否 |cpu类型，默认kvm64，如果开启了nested，cpu必须为host或max|
|» cpuUnits|body|integer¦null| 否 |cpu限制(单位:百分比)，列：10 为10%|
|» bwlimit|body|integer¦null| 否 |I/O限制（单位MB/S）|
|» arch|body|string¦null| 否 |系统架构(x86_64,arrch64)，默认x86_64|
|» acpi|body|string¦null| 否 |acpi 默认1 开启|
|» memory|body|integer¦null| 否 |内存 单位Mb，默认512|
|» storage|body|string¦null| 否 |PVE磁盘名，auto为自动|
|» systemDiskSize|body|integer¦null| 否 |系统盘大小，单位Gb|
|» dataDisk|body|string| 否 |none|
|» bandwidth|body|integer¦null| 否 |带宽，单位Mbps|
|» onBoot|body|integer¦null| 否 |是否开机自启 0:否 1:是，默认0关闭|
|» name|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除配置模板

DELETE /{adminPath}/deleteConfiguretemplate/{id}

删除配置模板

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|path|integer| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改配置模板

PUT /{adminPath}/updateConfiguretemplate

修改配置模板

> Body 请求参数

```json
{
  "configureTemplateId": 0,
  "sockets": 0,
  "cores": 0,
  "threads": 0,
  "devirtualization": true,
  "kvm": true,
  "cpuModel": 0,
  "modelGroup": 0,
  "nested": true,
  "cpu": "string",
  "cpuUnits": 0,
  "bwlimit": 0,
  "arch": "string",
  "acpi": "string",
  "memory": 0,
  "storage": "string",
  "systemDiskSize": 0,
  "dataDisk": "string",
  "bandwidth": 0,
  "onBoot": 0,
  "name": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» configureTemplateId|body|integer¦null| 否 |配置模板ID|
|» sockets|body|integer¦null| 否 |插槽数，默认为1|
|» cores|body|integer¦null| 否 |核心，默认1|
|» threads|body|integer¦null| 否 |线程数，默认1|
|» devirtualization|body|boolean¦null| 否 |是否去虚拟化，默认false|
|» kvm|body|boolean¦null| 否 |是否开启kvm虚拟化，默认开启|
|» cpuModel|body|integer¦null| 否 |cpu信息模型ID|
|» modelGroup|body|integer¦null| 否 |组合模型ID，优先级大于cpuModel|
|» nested|body|boolean¦null| 否 |是否开启嵌套虚拟化，默认关闭|
|» cpu|body|string¦null| 否 |cpu类型，默认kvm64，如果开启了nested，cpu必须为host或max|
|» cpuUnits|body|integer¦null| 否 |cpu限制(单位:百分比)，列：10 为10%|
|» bwlimit|body|integer¦null| 否 |I/O限制（单位MB/S）|
|» arch|body|string¦null| 否 |系统架构(x86_64,arrch64)，默认x86_64|
|» acpi|body|string¦null| 否 |acpi 默认1 开启|
|» memory|body|integer¦null| 否 |内存 单位Mb，默认512|
|» storage|body|string¦null| 否 |PVE磁盘名，auto为自动|
|» systemDiskSize|body|integer¦null| 否 |系统盘大小，单位Gb|
|» dataDisk|body|string| 否 |none|
|» bandwidth|body|integer¦null| 否 |带宽，单位Mbps|
|» onBoot|body|integer¦null| 否 |是否开机自启 0:否 1:是，默认0关闭|
|» name|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询配置模板

GET /{adminPath}/getConfiguretemplateByPage

分页查询配置模板

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |页码|
|limit|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/实例管理/实例操作

## PUT 电源状态操作

PUT /{adminPath}/power/{hostId}/{action}

虚拟机电源状态管理，action类型有start=开机、stop=关机、reboot=重启、shutdown=强制关机、suspend=挂起、resume=恢复、pause=暂停、unpause=恢复

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|hostId|path|integer| 是 |数据库中虚拟机ID（非vmid）|
|action|path|string| 是 |action类型有start、stop、reboot、shutdown|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 重装虚拟机系统

PUT /{adminPath}/reinstall

> Body 请求参数

```json
{
  "hostId": 0,
  "os": "string",
  "newPassword": "string",
  "resetDataDisk": true
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |hostId，非vmId|
|» os|body|string| 是 |系统全面或系统id|
|» newPassword|body|string¦null| 是 |新密码，为空不重置|
|» resetDataDisk|body|boolean| 是 |是否重置数据盘|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除虚拟机

DELETE /{adminPath}/delete/{hostId}

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|hostId|path|integer| 是 |非虚拟机vmId，为ID|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/系统设置

## GET 获取被控通讯密钥

GET /{adminPath}/getControlledSecretKey

获取被控通讯密钥

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "dbb77f27239249c49bbf743a6b6063e31"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## GET 获取全局虚拟机默认系统盘大小

GET /{adminPath}/getVmDefaultDiskSize

获取全局虚拟机默认系统盘大小

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "Linux": 40,
    "Windows": 60
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 修改全局虚拟机默认系统盘大小

POST /{adminPath}/updateVmDefaultDiskSize

修改全局虚拟机默认系统盘大小，该接口支持POST,PUT请求方法

> Body 请求参数

```json
{
  "Linux": 0,
  "Windows": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» Linux|body|integer¦null| 是 |单位GB|
|» Windows|body|integer¦null| 是 |单位GB|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|null|true|none||none|

# 后台/限制功能/CPU模型管理

## POST 新增cpu信息模型

POST /{adminPath}/addCpuInfo

新增cpu信息模型

> Body 请求参数

```json
{
  "name": "12th Gen Intel(R) Core(TM) i9-12900KS @ 5.50GHz",
  "family": 6,
  "model": 97,
  "stepping": 2,
  "level": "0xEC",
  "xlevel": "0x8000001E",
  "vendor": "GenuineIntel",
  "l3Cache": true
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» name|body|string¦null| 否 |cpu名称|
|» family|body|integer¦null| 否 |CPU系列|
|» model|body|integer¦null| 否 |型号|
|» stepping|body|integer¦null| 否 |步进|
|» level|body|string¦null| 否 |CPU型号|
|» xlevel|body|string¦null| 否 |CPU扩展型号|
|» vendor|body|string¦null| 否 |厂商|
|» l3Cache|body|boolean¦null| 否 |三缓|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## DELETE 删除cpu信息模型

DELETE /{adminPath}/deleteCpuInfo

删除cpu信息模型

> Body 请求参数

```json
{
  "id": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer¦null| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改cpu信息模型

PUT /{adminPath}/updateCpuInfo

修改cpu信息模型

> Body 请求参数

```json
{
  "id": 0,
  "name": "string",
  "family": 0,
  "model": 0,
  "stepping": 0,
  "level": "string",
  "xlevel": "string",
  "vendor": "string",
  "l3Cache": true
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string¦null| 否 |cpu名称|
|» family|body|integer¦null| 否 |CPU系列|
|» model|body|integer¦null| 否 |型号|
|» stepping|body|integer¦null| 否 |步进|
|» level|body|string¦null| 否 |CPU型号|
|» xlevel|body|string¦null| 否 |CPU扩展型号|
|» vendor|body|string¦null| 否 |厂商|
|» l3Cache|body|boolean¦null| 否 |三缓|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询cpu信息模型

GET /{adminPath}/selectCpuInfoPage

分页查询cpu信息模型

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|limit|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "cpu": null,
        "name": "12th Gen Intel(R) Core(TM) i9-12900KS @ 5.50GHz",
        "family": 6,
        "model": 97,
        "stepping": 2,
        "level": "0xEC",
        "xlevel": "0x8000001E",
        "vendor": "GenuineIntel",
        "l3Cache": true,
        "other": null,
        "createDate": 1692536103329
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» cpu|null|false|none||none|
|»»» name|string|false|none||none|
|»»» family|integer|false|none||none|
|»»» model|integer|false|none||none|
|»»» stepping|integer|false|none||none|
|»»» level|string|false|none||none|
|»»» xlevel|string|false|none||none|
|»»» vendor|string|false|none||none|
|»»» l3Cache|boolean|false|none||none|
|»»» other|null|false|none||none|
|»»» createDate|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

## GET 查询cpu信息模型

GET /{adminPath}/selectCpuInfo

查询cpu信息模型

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|query|integer| 是 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/限制功能/硬件模型管理

## POST 新增smbios信息模型

POST /{adminPath}/addSmbiosInfo

新增smbios信息模型

> Body 请求参数

```json
{
  "model": {
    "manufacturer": "ASUS",
    "product": "ROG MAXIMUS Z690 EXTREME",
    "version": "2022.1"
  },
  "info": "计算机系统的信息，包括制造商、产品名称、版本号、序列号等",
  "type": 1
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» type|body|string¦null| 否 |none|
|» model|body|object| 是 |none|
|» info|body|string| 是 |备注|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除smbios信息模型

DELETE /{adminPath}/deleteSmbiosInfo

删除smbios信息模型

> Body 请求参数

```json
{
  "id": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改smbios信息模型

PUT /{adminPath}/updateSmbiosInfo

修改smbios信息模型

> Body 请求参数

```json
{
  "id": 0,
  "type": "string",
  "model": {},
  "info": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» type|body|string¦null| 否 |none|
|» model|body|object| 是 |none|
|» info|body|string| 是 |备注|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 查询smbios信息模型

GET /{adminPath}/getSmbiosInfo

查询smbios信息模型

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询smbios信息模型

GET /{adminPath}/getSmbiosInfoList

分页查询smbios信息模型

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|limit|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "type": 0,
        "model": {
          "version": "Intel-Core"
        },
        "info": "BIOS版本号",
        "createDate": 1692536821707
      },
      {
        "id": 2,
        "type": 1,
        "model": {
          "product": "ROG MAXIMUS Z690 EXTREME",
          "version": "2022.1",
          "manufacturer": "ASUS"
        },
        "info": "计算机系统的信息，包括制造商、产品名称、版本号、序列号等",
        "createDate": 1692536955738
      },
      {
        "id": 3,
        "type": 2,
        "model": {
          "product": "Intel Z690",
          "version": "2022.5",
          "manufacturer": "Intel"
        },
        "info": "主板（基板）的信息，包括制造商、版本号、序列号等",
        "createDate": 1692537042415
      },
      {
        "id": 4,
        "type": 3,
        "model": {
          "manufacturer": "Chuqi Cloud"
        },
        "info": "计算机系统的机箱或外壳信息，包括类型、制造商、版本号等",
        "createDate": 1692537114875
      },
      {
        "id": 5,
        "type": 4,
        "model": {
          "max-speed": "4800",
          "manufacturer": "Intel",
          "current-speed": "4800"
        },
        "info": "计算机中的处理器的信息，包括类型、制造商、频率等",
        "createDate": 1692537183084
      },
      {
        "id": 6,
        "type": 17,
        "model": {
          "part": "Chuqi Cloud",
          "speed": "4800",
          "serial": "1248DC",
          "loc_pfx": "DDR5",
          "manufacturer": "Samsung"
        },
        "info": "内存设备（模块）的信息，如容量、速度、位置等",
        "createDate": 1692537313473
      }
    ],
    "total": 6,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/限制功能/组合模板

## POST 添加模型组

POST /{adminPath}/addModelGroup

添加模型组

> Body 请求参数

```json
{
  "cpuModel": 0,
  "smbiosModel": "string",
  "info": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» cpuModel|body|integer¦null| 否 |cpu模型id|
|» smbiosModel|body|string¦null| 否 |smbios模型id集|
|» info|body|string| 是 |备注|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除模型组

DELETE /{adminPath}/deleteModelGroup

删除模型组

> Body 请求参数

```json
{
  "id": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改模型组

PUT /{adminPath}/updateModelGroup

修改模型组

> Body 请求参数

```json
{
  "id": 0,
  "cpuModel": 0,
  "smbiosModel": "string",
  "info": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» cpuModel|body|integer¦null| 否 |cpu模型id|
|» smbiosModel|body|string¦null| 否 |smbios模型id集|
|» info|body|string| 是 |备注|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 查询模型组

GET /{adminPath}/getModelGroup

查询模型组

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|modelGroupId|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询模型组

GET /{adminPath}/getModelGroupPage

分页查询模型组

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |none|
|size|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 后台/VNC控制器管理

## POST 增加vnc控制器节点

POST /{adminPath}/addVncNode

增加vnc控制器节点

> Body 请求参数

```json
{
  "name": "本地测试",
  "host": "192.168.36.155",
  "port": 7600,
  "domain": "192.168.36.155",
  "status": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string| 是 |none|
|» host|body|string| 是 |none|
|» port|body|integer| 是 |none|
|» domain|body|string| 是 |对外公开的域名|
|» protocol|body|integer¦null| 否 |ssl，默认0不开启|
|» status|body|integer| 是 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "添加成功"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## DELETE 删除vnc控制器节点

DELETE /{adminPath}/deleteVncNode

删除vnc控制器节点

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|id|query|integer| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{
  "code": 0,
  "message": "string",
  "data": "string"
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string|true|none||none|

## PUT 修改vnc控制器节点

PUT /{adminPath}/updateVncNode

修改vnc控制器节点

> Body 请求参数

```json
{
  "id": 0,
  "name": "string",
  "host": "string",
  "port": 0,
  "domain": "string",
  "protocol": 0,
  "status": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |none|
|» name|body|string| 是 |none|
|» host|body|string| 是 |none|
|» port|body|integer| 是 |none|
|» domain|body|string| 是 |对外公开的域名|
|» protocol|body|integer¦null| 否 |ssl，默认0不开启|
|» status|body|integer| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 分页查询vnc控制器节点

GET /{adminPath}/selectVncNodePage

分页查询vnc控制器节点

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|page|query|integer| 否 |页码|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "本地测试",
        "host": "192.168.36.155",
        "port": 7600,
        "domain": "192.168.36.155",
        "status": 0,
        "createDate": 1700731222313
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1,
    "orders": [],
    "optimizeCountSql": true,
    "searchCount": true,
    "maxLimit": null,
    "countId": null,
    "pages": 1
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» records|[object]|true|none||none|
|»»» id|integer|false|none||none|
|»»» name|string|false|none||none|
|»»» host|string|false|none||none|
|»»» port|integer|false|none||none|
|»»» domain|string|false|none||none|
|»»» status|integer|false|none||none|
|»»» createDate|integer|false|none||none|
|»» total|integer|true|none||none|
|»» size|integer|true|none||none|
|»» current|integer|true|none||none|
|»» orders|[string]|true|none||none|
|»» optimizeCountSql|boolean|true|none||none|
|»» searchCount|boolean|true|none||none|
|»» maxLimit|null|true|none||none|
|»» countId|null|true|none||none|
|»» pages|integer|true|none||none|

# API

## POST 创建虚拟机

POST /api/v1/{nodeType}/cerateVM

创建虚拟机

> Body 请求参数

```json
{
  "nodeid": 1,
  "hostname": "cloud-test01",
  "sockets": 1,
  "cores": 2,
  "threads": 2,
  "nested": false,
  "modelGroup": 2,
  "cpu": "host",
  "arch": "x86_64",
  "memory": 512,
  "storage": "auto",
  "systemDiskSize": 40,
  "os": "CentOS-8-Stream-x64.qcow2",
  "osType": "linux",
  "bandwidth": 100,
  "ipConfig": {
    "1": ""
  },
  "dataDisk": {
    "1": 40
  },
  "onBoot": 1,
  "username": "root",
  "password": "123456"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |虚拟化平台，目前只有pve|
|body|body|object| 否 |none|
|» nodeid|body|integer| 是 |节点ID|
|» hostname|body|string¦null| 否 |虚拟机名|
|» configureTemplateId|body|integer¦null| 否 |配置模板ID|
|» sockets|body|integer¦null| 否 |插槽数，默认为1|
|» cores|body|integer¦null| 否 |核心，默认1|
|» threads|body|integer¦null| 否 |线程数，默认1|
|» devirtualization|body|boolean¦null| 否 |是否去虚拟化，默认false|
|» kvm|body|boolean¦null| 否 |是否开启kvm虚拟化，默认开启|
|» cpuModel|body|integer¦null| 否 |cpu信息模型ID|
|» modelGroup|body|integer¦null| 否 |组合模型ID，优先级大于cpuModel|
|» nested|body|boolean¦null| 否 |是否开启嵌套虚拟化，默认关闭|
|» cpu|body|string¦null| 否 |cpu类型，默认kvm64，如果开启了nested，cpu必须为host或max|
|» cpuUnits|body|integer¦null| 否 |cpu限制(单位:百分比)，列：10 为10%|
|» bwlimit|body|integer¦null| 否 |I/O限制（单位MB/S）|
|» arch|body|string¦null| 否 |系统架构(x86_64,arrch64)，默认x86_64|
|» acpi|body|string¦null| 否 |acpi 默认1 开启|
|» memory|body|integer¦null| 否 |内存 单位Mb，默认512|
|» storage|body|string¦null| 否 |PVE磁盘名，auto为自动|
|» systemDiskSize|body|integer¦null| 否 |系统盘大小，单位Gb|
|» dataDisk|body|string| 否 |none|
|» bridge|body|string¦null| 否 |网桥|
|» ipConfig|body|string| 否 |none|
|» os|body|string| 是 |操作系统，可填镜像名称或id|
|» osType|body|string¦null| 否 |操作系统类型，（windows|linux）|
|» bandwidth|body|integer¦null| 否 |带宽，单位Mbps|
|» onBoot|body|integer¦null| 否 |是否开机自启 0:否 1:是，默认0关闭|
|» username|body|string¦null| 否 |虚拟机登录用户名|
|» password|body|string¦null| 否 |虚拟机登录密码|
|» expirationTime|body|integer¦null| 否 |到期时间，时间戳|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "nodeid": 1,
    "hostid": 34,
    "vmid": 102,
    "hostname": "cloud-test01",
    "configureTemplateId": null,
    "sockets": 1,
    "cores": 2,
    "threads": 2,
    "nested": false,
    "devirtualization": false,
    "kvm": true,
    "cpuModel": null,
    "modelGroup": 2,
    "cpu": "host",
    "cpuUnits": 1024,
    "args": null,
    "arch": "x86_64",
    "acpi": 1,
    "memory": 512,
    "swap": null,
    "storage": "local-lvm",
    "systemDiskSize": 40,
    "dataDisk": {
      "1": 40
    },
    "bridge": "vmbr0",
    "ipConfig": {
      "1": "ip=192.168.36.1/24,gw=192.168.36.2"
    },
    "dns1": "114.114.114.114",
    "os": "CentOS-8-Stream-x64.qcow2",
    "osType": "linux",
    "iso": null,
    "template": null,
    "onBoot": 1,
    "bandwidth": 100,
    "username": "root",
    "password": "123456",
    "task": null,
    "status": null,
    "expirationTime": null
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» nodeid|integer|true|none||none|
|»» hostid|integer|true|none||none|
|»» vmid|integer|true|none||none|
|»» hostname|string|true|none||none|
|»» configureTemplateId|null|true|none||none|
|»» sockets|integer|true|none||none|
|»» cores|integer|true|none||none|
|»» threads|integer|true|none||none|
|»» nested|boolean|true|none||none|
|»» devirtualization|boolean|true|none||none|
|»» kvm|boolean|true|none||none|
|»» cpuModel|null|true|none||none|
|»» modelGroup|integer|true|none||none|
|»» cpu|string|true|none||none|
|»» cpuUnits|integer|true|none||none|
|»» args|null|true|none||none|
|»» arch|string|true|none||none|
|»» acpi|integer|true|none||none|
|»» memory|integer|true|none||none|
|»» swap|null|true|none||none|
|»» storage|string|true|none||none|
|»» systemDiskSize|integer|true|none||none|
|»» dataDisk|object|true|none||none|
|»»» 1|integer|true|none||none|
|»» bridge|string|true|none||none|
|»» ipConfig|object|true|none||none|
|»»» 1|string|true|none||none|
|»» dns1|string|true|none||none|
|»» os|string|true|none||none|
|»» osType|string|true|none||none|
|»» iso|null|true|none||none|
|»» template|null|true|none||none|
|»» onBoot|integer|true|none||none|
|»» bandwidth|integer|true|none||none|
|»» username|string|true|none||none|
|»» password|string|true|none||none|
|»» task|null|true|none||none|
|»» status|null|true|none||none|
|»» expirationTime|null|true|none||none|

## GET 获取指定虚拟机数据

GET /api/v1/{nodeType}/getVmInfo

current字段为当前运行实时监控数据，rrddata为历史数据，历史数据默认为一天

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |虚拟化平台，目前只有pve|
|hostId|query|integer| 否 |虚拟机ID（可以vmid或者键ID）|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "nodeName": null,
    "area": "中国",
    "vmhost": {
      "id": 13,
      "nodeid": 1,
      "vmid": 101,
      "name": "qimen",
      "configureTemplateId": null,
      "sockets": 2,
      "cores": 2,
      "threads": 2,
      "devirtualization": false,
      "kvm": true,
      "cpuModel": null,
      "modelGroup": null,
      "cpu": "kvm64",
      "cpuUnits": 1024,
      "bwlimit": 512000,
      "args": null,
      "arch": "x86_64",
      "acpi": 1,
      "memory": 1024,
      "swap": null,
      "agent": 1,
      "username": "root",
      "password": "123456",
      "ide0": null,
      "ide2": "local-lvm:cloudinit",
      "net0": "virtio,bridge=vmbr0",
      "net1": null,
      "os": "CentOS-8-Stream-x64.qcow2",
      "osName": "CentOS-8-Stream-x64",
      "osType": "linux",
      "iso": null,
      "template": null,
      "onBoot": 0,
      "bandwidth": 1000,
      "storage": "local-lvm",
      "systemDiskSize": 40,
      "dataDisk": null,
      "bridge": "vmbr0",
      "ipConfig": {
        "1": "ip=192.168.36.3/24,gw=192.168.36.2"
      },
      "ipData": [
        {
          "ip": "192.168.36.3",
          "subnetMask": 24,
          "gateway": "192.168.36.2"
        }
      ],
      "nested": 0,
      "task": {
        "1700308921645": 203,
        "1700308936706": 204,
        "1700308938728": 205,
        "1700308940746": 206
      },
      "status": 0,
      "createTime": 1700308921584,
      "expirationTime": 2015668921584,
      "ipList": [
        "192.168.36.3"
      ]
    },
    "os": {
      "id": 3,
      "name": "CentOS-8-Stream-x64",
      "fileName": "CentOS-8-Stream-x64.qcow2",
      "type": "linux",
      "arch": "x86_64",
      "osType": "centos",
      "nodeStatus": {
        "0": {
          "nodeId": 1,
          "status": 2,
          "nodeName": "pve",
          "schedule": 100
        }
      },
      "downType": 0,
      "url": "https://mirror.chuqiyun.com/cloud-images/centos/CentOS-8-Stream-x64.qcow2",
      "size": "671MB",
      "path": "/home/images/",
      "cloud": 1,
      "status": 0,
      "reason": null,
      "createTime": 1698662508139
    },
    "current": {
      "data": {
        "nics": {
          "tap101i0": {
            "netin": 35462,
            "netout": 12169
          }
        },
        "freemem": 77418496,
        "running-qemu": "7.2.0",
        "maxmem": 1073741824,
        "diskread": 563062450,
        "status": "running",
        "cpus": 4,
        "ballooninfo": {
          "last_update": 1700894642,
          "mem_swapped_out": 48791552,
          "minor_page_faults": 705922,
          "total_mem": 807354368,
          "major_page_faults": 1902,
          "mem_swapped_in": 3076096,
          "free_mem": 77418496,
          "max_mem": 1073741824,
          "actual": 1073741824
        },
        "blockstat": {
          "ide2": {
            "wr_highest_offset": 0,
            "unmap_operations": 0,
            "wr_merged": 0,
            "failed_rd_operations": 0,
            "wr_operations": 0,
            "rd_bytes": 278706,
            "idle_time_ns": 384402393488,
            "timed_stats": [],
            "flush_total_time_ns": 0,
            "invalid_rd_operations": 0,
            "rd_total_time_ns": 41182463,
            "failed_wr_operations": 0,
            "failed_flush_operations": 0,
            "account_failed": true,
            "invalid_wr_operations": 0,
            "invalid_flush_operations": 0,
            "unmap_total_time_ns": 0,
            "wr_total_time_ns": 0,
            "invalid_unmap_operations": 0,
            "unmap_merged": 0,
            "unmap_bytes": 0,
            "account_invalid": true,
            "flush_operations": 0,
            "failed_unmap_operations": 0,
            "rd_merged": 0,
            "wr_bytes": 0,
            "rd_operations": 80
          },
          "scsi0": {
            "account_invalid": true,
            "flush_operations": 154,
            "unmap_bytes": 0,
            "wr_bytes": 76163584,
            "rd_operations": 14379,
            "failed_unmap_operations": 0,
            "rd_merged": 0,
            "unmap_total_time_ns": 0,
            "invalid_wr_operations": 0,
            "invalid_flush_operations": 0,
            "unmap_merged": 0,
            "wr_total_time_ns": 5255532132,
            "invalid_unmap_operations": 0,
            "flush_total_time_ns": 46447197,
            "idle_time_ns": 11116062098,
            "timed_stats": [],
            "failed_flush_operations": 0,
            "failed_wr_operations": 0,
            "account_failed": true,
            "invalid_rd_operations": 0,
            "rd_total_time_ns": 12363240977,
            "wr_highest_offset": 42006446080,
            "unmap_operations": 0,
            "wr_operations": 1470,
            "rd_bytes": 562783744,
            "wr_merged": 0,
            "failed_rd_operations": 0
          }
        },
        "ha": {
          "managed": 0
        },
        "cpu": 0.0219741222906965,
        "name": "qimen",
        "running-machine": "pc-i440fx-7.2+pve0",
        "diskwrite": 76163584,
        "serial": 1,
        "netin": 35462,
        "pid": 7768,
        "uptime": 415,
        "vmid": 101,
        "agent": 1,
        "netout": 12169,
        "proxmox-support": {
          "pbs-dirty-bitmap-migration": true,
          "pbs-masterkey": true,
          "query-bitmap-info": true,
          "backup-max-workers": true,
          "pbs-dirty-bitmap-savevm": true,
          "pbs-library-version": "1.3.1 (4d450bb294cac5316d2f23bf087c4b02c0543d79)",
          "pbs-dirty-bitmap": true
        },
        "maxdisk": 42949672960,
        "qmpstatus": "running",
        "disk": 0,
        "balloon": 1073741824,
        "mem": 729935872
      }
    },
    "rrddata": {
      "data": [
        {
          "time": 1700890500
        },
        {
          "time": 1700890560
        },
        {
          "time": 1700890620
        },
        {
          "time": 1700890680
        },
        {
          "time": 1700890740
        },
        {
          "time": 1700890800
        },
        {
          "time": 1700890860
        },
        {
          "time": 1700890920
        },
        {
          "time": 1700890980
        },
        {
          "time": 1700891040
        },
        {
          "time": 1700891100
        },
        {
          "time": 1700891160
        },
        {
          "time": 1700891220
        },
        {
          "time": 1700891280
        },
        {
          "time": 1700891340
        },
        {
          "maxdisk": 42949672960,
          "time": 1700891400,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxdisk": 42949672960,
          "time": 1700891460,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700891520,
          "maxdisk": 42949672960
        },
        {
          "time": 1700891580,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700891640
        },
        {
          "time": 1700891700,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700891760
        },
        {
          "time": 1700891820,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "maxdisk": 42949672960,
          "time": 1700891880,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "time": 1700891940
        },
        {
          "maxdisk": 42949672960,
          "time": 1700892000,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "time": 1700892060
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700892120
        },
        {
          "maxdisk": 42949672960,
          "time": 1700892180,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxdisk": 42949672960,
          "time": 1700892240,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700892300
        },
        {
          "maxdisk": 42949672960,
          "time": 1700892360,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700892420
        },
        {
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "time": 1700892480
        },
        {
          "maxdisk": 42949672960,
          "time": 1700892540,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700892600
        },
        {
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824,
          "time": 1700892660,
          "maxdisk": 42949672960
        },
        {
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824,
          "time": 1700892720,
          "maxdisk": 42949672960
        },
        {
          "time": 1700892780,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700892840,
          "maxdisk": 42949672960
        },
        {
          "time": 1700892900,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824
        },
        {
          "time": 1700892960,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "maxdisk": 42949672960,
          "time": 1700893020,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700893080
        },
        {
          "time": 1700893140,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824
        },
        {
          "time": 1700893200,
          "maxdisk": 42949672960,
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700893260,
          "maxdisk": 42949672960
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700893320,
          "maxdisk": 42949672960
        },
        {
          "maxdisk": 42949672960,
          "time": 1700893380,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxdisk": 42949672960,
          "time": 1700893440,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700893500
        },
        {
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824,
          "time": 1700893560,
          "maxdisk": 42949672960
        },
        {
          "maxdisk": 42949672960,
          "time": 1700893620,
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4
        },
        {
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824,
          "time": 1700893680,
          "maxdisk": 42949672960
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700893740,
          "maxdisk": 42949672960
        },
        {
          "time": 1700893800,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824
        },
        {
          "maxcpu": 4,
          "disk": 0,
          "maxmem": 1073741824,
          "time": 1700893860,
          "maxdisk": 42949672960
        },
        {
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "time": 1700893920
        },
        {
          "maxdisk": 42949672960,
          "time": 1700893980,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "maxmem": 1073741824,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "time": 1700894040
        },
        {
          "maxdisk": 42949672960,
          "time": 1700894100,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "maxmem": 1073741824,
          "time": 1700894160,
          "maxdisk": 42949672960
        },
        {
          "maxdisk": 42949672960,
          "time": 1700894220,
          "maxmem": 1073741824,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "disk": 0,
          "maxcpu": 4,
          "mem": 387054295.322034,
          "cpu": 0.387478816192326,
          "netout": 159.785714285714,
          "maxdisk": 42949672960,
          "maxmem": 1073741824,
          "time": 1700894280,
          "diskwrite": 1430548.89795918,
          "netin": 450.773469387755,
          "diskread": 11307531.9918367
        },
        {
          "netout": 12.0716666666667,
          "cpu": 0.0238379856951775,
          "maxdisk": 42949672960,
          "maxcpu": 4,
          "disk": 0,
          "mem": 725369924.266667,
          "diskread": 97443.84,
          "time": 1700894340,
          "diskwrite": 52933.9733333333,
          "netin": 35.285,
          "maxmem": 1073741824
        },
        {
          "mem": 728859989.333333,
          "disk": 0,
          "maxcpu": 4,
          "maxdisk": 42949672960,
          "cpu": 0.022700290662013,
          "netout": 5.88666666666667,
          "diskread": 5529.6,
          "maxmem": 1073741824,
          "netin": 19.15,
          "diskwrite": 5126.82666666667,
          "time": 1700894400
        },
        {
          "netin": 17.4166666666667,
          "time": 1700894460,
          "diskwrite": 2501.97333333333,
          "maxmem": 1073741824,
          "diskread": 18773.3333333333,
          "maxdisk": 42949672960,
          "cpu": 0.023346304181318,
          "netout": 2.65,
          "mem": 739424802.133333,
          "maxcpu": 4,
          "disk": 0
        },
        {
          "diskread": 0,
          "maxmem": 1073741824,
          "netin": 21.4,
          "time": 1700894520,
          "diskwrite": 2182.82666666667,
          "mem": 741955857.066667,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "netout": 7.86666666666667,
          "cpu": 0.0237573758742922
        },
        {
          "maxdisk": 42949672960,
          "netout": 34.735,
          "cpu": 0.0222518400776516,
          "mem": 741650295.466667,
          "maxcpu": 4,
          "disk": 0,
          "diskread": 136.533333333333,
          "netin": 94.8716666666667,
          "time": 1700894580,
          "diskwrite": 947.2,
          "maxmem": 1073741824
        },
        {
          "mem": 736406186.666667,
          "maxcpu": 4,
          "disk": 0,
          "maxdisk": 42949672960,
          "netout": 9.115,
          "cpu": 0.022881902347377,
          "maxmem": 1073741824,
          "netin": 34.145,
          "time": 1700894640,
          "diskwrite": 37411.84,
          "diskread": 819.2
        }
      ]
    }
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» info|object|true|none||none|
|»»» id|integer|true|none||none|
|»»» nodeid|integer|true|none||none|
|»»» vmid|integer|true|none||none|
|»»» name|string|true|none||none|
|»»» cores|integer|true|none||none|
|»»» memory|integer|true|none||none|
|»»» agent|integer|true|none||none|
|»»» ide0|string|true|none||none|
|»»» ide2|string¦null|true|none||none|
|»»» net0|string|true|none||none|
|»»» net1|string¦null|true|none||none|
|»»» os|string|true|none||none|
|»»» bandwidth|integer|true|none||none|
|»»» storage|string|true|none||none|
|»»» systemDiskSize|integer|true|none||none|
|»»» dataDisk|object|true|none||none|
|»»»» 1|integer|true|none||none|
|»»» bridge|string|true|none||none|
|»»» ipConfig|object|true|none||none|
|»»»» 1|string|true|none||none|
|»»» nested|integer|true|none||none|
|»»» task|object|true|none||none|
|»»»» 1689664264882|integer|true|none||none|
|»»»» 1689664264900|integer|true|none||none|
|»»»» 1689664279791|integer|true|none||none|
|»»»» 1689664280959|integer|true|none||none|
|»»»» 1689664282054|integer|true|none||none|
|»»»» 1689664282539|integer|true|none||none|
|»»» status|string|true|none||none|
|»»» createTime|integer|true|none||none|
|»»» expirationTime|integer|true|none||none|
|»» status|object|true|none||none|
|»»» running-machine|string|true|none||none|
|»»» agent|integer|true|none||none|
|»»» pid|integer|true|none||none|
|»»» diskread|integer|true|none||none|
|»»» uptime|integer|true|none||none|
|»»» mem|integer|true|none||none|
|»»» vmid|integer|true|none||none|
|»»» freemem|integer|true|none||none|
|»»» maxmem|integer|true|none||none|
|»»» cpus|integer|true|none||none|
|»»» cpu|number|true|none||none|
|»»» balloon|integer|true|none||none|
|»»» nics|object|true|none||none|
|»»»» tap101i0|object|true|none||none|
|»»»»» netin|integer|true|none||none|
|»»»»» netout|integer|true|none||none|
|»»» netin|integer|true|none||none|
|»»» blockstat|object|true|none||none|
|»»»» scsi1|object|true|none||none|
|»»»»» wr_operations|integer|true|none||none|
|»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»» idle_time_ns|integer|true|none||none|
|»»»»» timed_stats|[string]|true|none||none|
|»»»»» account_failed|boolean|true|none||none|
|»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»» rd_bytes|integer|true|none||none|
|»»»»» rd_merged|integer|true|none||none|
|»»»»» failed_rd_operations|integer|true|none||none|
|»»»»» account_invalid|boolean|true|none||none|
|»»»»» unmap_merged|integer|true|none||none|
|»»»»» failed_wr_operations|integer|true|none||none|
|»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»» wr_bytes|integer|true|none||none|
|»»»»» wr_merged|integer|true|none||none|
|»»»»» rd_operations|integer|true|none||none|
|»»»»» unmap_bytes|integer|true|none||none|
|»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»» unmap_operations|integer|true|none||none|
|»»»»» wr_highest_offset|integer|true|none||none|
|»»»»» flush_operations|integer|true|none||none|
|»»»»» failed_flush_operations|integer|true|none||none|
|»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»» invalid_rd_operations|integer|true|none||none|
|»»»» scsi0|object|true|none||none|
|»»»»» failed_rd_operations|integer|true|none||none|
|»»»»» account_invalid|boolean|true|none||none|
|»»»»» unmap_merged|integer|true|none||none|
|»»»»» failed_wr_operations|integer|true|none||none|
|»»»»» invalid_wr_operations|integer|true|none||none|
|»»»»» wr_operations|integer|true|none||none|
|»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»» timed_stats|[string]|true|none||none|
|»»»»» idle_time_ns|integer|true|none||none|
|»»»»» account_failed|boolean|true|none||none|
|»»»»» rd_bytes|integer|true|none||none|
|»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»» rd_merged|integer|true|none||none|
|»»»»» wr_highest_offset|integer|true|none||none|
|»»»»» unmap_operations|integer|true|none||none|
|»»»»» failed_flush_operations|integer|true|none||none|
|»»»»» flush_operations|integer|true|none||none|
|»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»» wr_bytes|integer|true|none||none|
|»»»»» wr_merged|integer|true|none||none|
|»»»»» unmap_bytes|integer|true|none||none|
|»»»»» rd_operations|integer|true|none||none|
|»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»» rd_total_time_ns|integer|true|none||none|
|»»»» ide2|object|true|none||none|
|»»»»» failed_unmap_operations|integer|true|none||none|
|»»»»» wr_bytes|integer|true|none||none|
|»»»»» wr_merged|integer|true|none||none|
|»»»»» unmap_bytes|integer|true|none||none|
|»»»»» rd_operations|integer|true|none||none|
|»»»»» invalid_flush_operations|integer|true|none||none|
|»»»»» rd_total_time_ns|integer|true|none||none|
|»»»»» unmap_operations|integer|true|none||none|
|»»»»» wr_highest_offset|integer|true|none||none|
|»»»»» failed_flush_operations|integer|true|none||none|
|»»»»» flush_operations|integer|true|none||none|
|»»»»» unmap_total_time_ns|integer|true|none||none|
|»»»»» flush_total_time_ns|integer|true|none||none|
|»»»»» invalid_rd_operations|integer|true|none||none|
|»»»»» wr_operations|integer|true|none||none|
|»»»»» wr_total_time_ns|integer|true|none||none|
|»»»»» timed_stats|[string]|true|none||none|
|»»»»» idle_time_ns|integer|true|none||none|
|»»»»» account_failed|boolean|true|none||none|
|»»»»» invalid_unmap_operations|integer|true|none||none|
|»»»»» rd_bytes|integer|true|none||none|
|»»»»» rd_merged|integer|true|none||none|
|»»»»» failed_rd_operations|integer|true|none||none|
|»»»»» account_invalid|boolean|true|none||none|
|»»»»» unmap_merged|integer|true|none||none|
|»»»»» failed_wr_operations|integer|true|none||none|
|»»»»» invalid_wr_operations|integer|true|none||none|
|»»» ballooninfo|object|true|none||none|
|»»»» actual|integer|true|none||none|
|»»»» minor_page_faults|integer|true|none||none|
|»»»» total_mem|integer|true|none||none|
|»»»» last_update|integer|true|none||none|
|»»»» mem_swapped_in|integer|true|none||none|
|»»»» free_mem|integer|true|none||none|
|»»»» max_mem|integer|true|none||none|
|»»»» mem_swapped_out|integer|true|none||none|
|»»»» major_page_faults|integer|true|none||none|
|»»» running-qemu|string|true|none||none|
|»»» disk|integer|true|none||none|
|»»» diskwrite|integer|true|none||none|
|»»» ha|object|true|none||none|
|»»»» managed|integer|true|none||none|
|»»» name|string|true|none||none|
|»»» qmpstatus|string|true|none||none|
|»»» proxmox-support|object|true|none||none|
|»»»» pbs-masterkey|boolean|true|none||none|
|»»»» pbs-library-version|string|true|none||none|
|»»»» pbs-dirty-bitmap-migration|boolean|true|none||none|
|»»»» query-bitmap-info|boolean|true|none||none|
|»»»» pbs-dirty-bitmap|boolean|true|none||none|
|»»»» pbs-dirty-bitmap-savevm|boolean|true|none||none|
|»»» maxdisk|integer|true|none||none|
|»»» netout|integer|true|none||none|
|»»» status|string|true|none||none|

## PUT 虚拟机电源状态管理

PUT /api/v1/{nodeType}/power/{hostId}/{action}

虚拟机电源状态管理，action类型有start=开机、stop=关机、reboot=重启、shutdown=强制关机、suspend=挂起、resume=恢复、pause=暂停、unpause=恢复

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |虚拟化平台，目前只有pve|
|hostId|path|string| 是 |数据库中虚拟机ID（非vmid）|
|action|path|string| 是 |action类型有start、stop、reboot、shutdown|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": null
}
```

```json
{
  "code": 20400,
  "message": "虚拟机不存在",
  "data": null
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|string¦null|true|none||none|

## PUT 重装系统

PUT /api/v1/{nodeType}/reinstall

重装系统

> Body 请求参数

```json
{
  "hostId": 0,
  "os": "string",
  "newPassword": "string",
  "resetDataDisk": true
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |hostId，非vmId|
|» os|body|string| 是 |系统全面或系统id|
|» newPassword|body|string¦null| 是 |新密码，为空不重置|
|» resetDataDisk|body|boolean| 是 |是否重置数据盘|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## DELETE 删除虚拟机

DELETE /api/v1/{nodeType}/delete/{hostId}

删除虚拟机

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |none|
|hostId|path|integer| 是 |这里不能为vmId|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取指定虚拟机的vnc地址

GET /api/v1/{node}/getVnc

获取指定虚拟机的vnc地址

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|node|path|string| 是 |none|
|hostId|query|integer| 否 |可以为vmid|
|page|query|integer| 否 |页数|
|size|query|integer| 否 |页数据量|
|Authorization|header|string| 否 |none|

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "total": 1,
    "current": 1,
    "pages": 1,
    "size": 5,
    "records": [
      {
        "本地测试": "http://192.168.36.155:6080/vnc.html?path=websockify/?token=qimen&port=59000"
      }
    ]
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» total|integer|true|none||none|
|»» current|integer|true|none||none|
|»» pages|integer|true|none||none|
|»» size|integer|true|none||none|
|»» records|[object]|true|none||none|
|»»» 本地测试|string|false|none||none|

## GET 通讯测试

GET /api/v1/status

通讯测试

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改指定虚拟机的VNC密码

PUT /{adminPath}/{node}/updateVncPassword

修改指定虚拟机的VNC密码

> Body 请求参数

```json
{
  "hostId": 0,
  "password": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|adminPath|path|string| 是 |none|
|node|path|string| 是 |none|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |none|
|» password|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 修改虚拟机密码

PUT /api/v1/{nodeType}/updateVmConfig/restPassword

修改虚拟机密码

> Body 请求参数

```json
{
  "hostId": 128,
  "newPassword": "test123"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |虚拟机的ID|
|» newPassword|body|string| 是 |新密码|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## PUT 续期

PUT /api/v1/{nodeType}/updateVmConfig/renewal

续期

> Body 请求参数

```json
{
  "hostId": 128,
  "expirationTime": 583883808973
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|nodeType|path|string| 是 |none|
|body|body|object| 否 |none|
|» hostId|body|integer| 是 |虚拟机ID|
|» expirationTime|body|integer| 是 |到期时间戳|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 通用接口

## GET 获取CPU类型

GET /api/common/cpuType

获取CPU类型

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "486": "Intel",
    "Conroe": "Intel",
    "Broadwell-IBRS": "Intel",
    "Skylake-Server": "Intel",
    "Broadwell-noTSX-IBRS": "Intel",
    "qemu32": "Common 32-bit QEMU CPU",
    "KnightsMill": "Intel",
    "athlon": "AMD",
    "IvyBridge-IBRS": "Intel",
    "Icelake-Client-noTSX": "Intel",
    "Cascadelake-Server": "Intel",
    "coreduo": "Intel",
    "host": "KVM processor with all supported host features",
    "Haswell": "Intel",
    "Skylake-Client": "Intel",
    "Skylake-Server-noTSX-IBRS": "Intel",
    "Icelake-Server-noTSX": "Intel",
    "Haswell-noTSX": "Intel",
    "Skylake-Server-IBRS": "Intel",
    "Icelake-Client": "Intel",
    "pentium2": "Intel",
    "pentium3": "Intel",
    "pentium": "Intel",
    "core2duo": "Intel",
    "Haswell-IBRS": "Intel",
    "Westmere": "Intel",
    "Icelake-Server": "Intel",
    "phenom": "AMD",
    "kvm64": "Common KVM processor",
    "Skylake-Client-noTSX-IBRS": "Intel",
    "IvyBridge": "Intel",
    "Haswell-noTSX-IBRS": "Intel",
    "Nehalem-IBRS": "Intel",
    "Broadwell": "Intel",
    "EPYC": "AMD",
    "kvm32": "Common KVM processor",
    "Cascadelake-Server-noTSX": "Intel",
    "EPYC-Rome": "AMD",
    "SandyBridge-IBRS": "Intel",
    "Broadwell-noTSX": "Intel",
    "Nehalem": "Intel",
    "max": "maximum supported CPU model",
    "EPYC-IBPB": "AMD",
    "qemu64": "Common 64-bit QEMU CPU",
    "EPYC-Milan": "AMD",
    "Skylake-Client-IBRS": "Intel",
    "Opteron_G1": "AMD",
    "Westmere-IBRS": "Intel",
    "Opteron_G2": "AMD",
    "Opteron_G3": "AMD",
    "Opteron_G4": "AMD",
    "Opteron_G5": "AMD",
    "SandyBridge": "Intel"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» 486|string|true|none||none|
|»» Conroe|string|true|none||none|
|»» Broadwell-IBRS|string|true|none||none|
|»» Skylake-Server|string|true|none||none|
|»» Broadwell-noTSX-IBRS|string|true|none||none|
|»» qemu32|string|true|none||none|
|»» KnightsMill|string|true|none||none|
|»» athlon|string|true|none||none|
|»» IvyBridge-IBRS|string|true|none||none|
|»» Icelake-Client-noTSX|string|true|none||none|
|»» Cascadelake-Server|string|true|none||none|
|»» coreduo|string|true|none||none|
|»» host|string|true|none||none|
|»» Haswell|string|true|none||none|
|»» Skylake-Client|string|true|none||none|
|»» Skylake-Server-noTSX-IBRS|string|true|none||none|
|»» Icelake-Server-noTSX|string|true|none||none|
|»» Haswell-noTSX|string|true|none||none|
|»» Skylake-Server-IBRS|string|true|none||none|
|»» Icelake-Client|string|true|none||none|
|»» pentium2|string|true|none||none|
|»» pentium3|string|true|none||none|
|»» pentium|string|true|none||none|
|»» core2duo|string|true|none||none|
|»» Haswell-IBRS|string|true|none||none|
|»» Westmere|string|true|none||none|
|»» Icelake-Server|string|true|none||none|
|»» phenom|string|true|none||none|
|»» kvm64|string|true|none||none|
|»» Skylake-Client-noTSX-IBRS|string|true|none||none|
|»» IvyBridge|string|true|none||none|
|»» Haswell-noTSX-IBRS|string|true|none||none|
|»» Nehalem-IBRS|string|true|none||none|
|»» Broadwell|string|true|none||none|
|»» EPYC|string|true|none||none|
|»» kvm32|string|true|none||none|
|»» Cascadelake-Server-noTSX|string|true|none||none|
|»» EPYC-Rome|string|true|none||none|
|»» SandyBridge-IBRS|string|true|none||none|
|»» Broadwell-noTSX|string|true|none||none|
|»» Nehalem|string|true|none||none|
|»» max|string|true|none||none|
|»» EPYC-IBPB|string|true|none||none|
|»» qemu64|string|true|none||none|
|»» EPYC-Milan|string|true|none||none|
|»» Skylake-Client-IBRS|string|true|none||none|
|»» Opteron_G1|string|true|none||none|
|»» Westmere-IBRS|string|true|none||none|
|»» Opteron_G2|string|true|none||none|
|»» Opteron_G3|string|true|none||none|
|»» Opteron_G4|string|true|none||none|
|»» Opteron_G5|string|true|none||none|
|»» SandyBridge|string|true|none||none|

## GET 获取OS类型

GET /api/common/osType

获取OS类型

> 返回示例

> 200 Response

```json
{
  "code": 0,
  "message": "string",
  "data": [
    "string"
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|[string]|true|none||none|

## GET 获取OS架构列表

GET /api/common/osArch

获取OS架构列表

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": [
    "x86_64",
    "arrch64"
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|[string]|true|none||none|

## GET 获取系统版本信息

GET /api/common/version

获取系统版本信息

> 返回示例

> 成功

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "buildVersion": "1.1.0_5",
    "name": "QimenIDC Community Edition",
    "description": "Open source, free, cloud-native multi-cloud management and hybrid cloud convergence system.",
    "version": "1.1.0"
  }
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

状态码 **200**

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|» code|integer|true|none||none|
|» message|string|true|none||none|
|» data|object|true|none||none|
|»» buildVersion|string|true|none||none|
|»» name|string|true|none||none|
|»» description|string|true|none||none|
|»» version|string|true|none||none|

# 受控端

## GET 状态查询

GET /status

获取状态

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取指定目录文件列表

GET /pathFile

获取指定目录文件列表

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|path|query|string| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 下载文件到指定目录

GET /wget

下载文件到指定目录

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|url|query|string| 否 |none|
|path|query|string| 否 |none|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 重置虚拟机密码

POST /changePassword

重置虚拟机密码

> Body 请求参数

```json
{
  "id": 108,
  "username": "administrator",
  "password": "qwe@123"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» id|body|integer| 是 |vmid|
|» username|body|string| 是 |用户名|
|» password|body|string| 是 |新密码|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## GET 获取版本号

GET /version

获取版本号

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 删除指定目录下的指定文件

POST /deleteFile

删除指定目录下的指定文件

> Body 请求参数

```json
{
  "path": "string",
  "file": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» path|body|string| 是 |none|
|» file|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 更新程序

POST /update

更新程序

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 导入磁盘到虚拟机

POST /importDisk

导入磁盘到虚拟机

> Body 请求参数

```json
{
  "vmid": 0,
  "image_path": "string",
  "save_path": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» vmid|body|integer| 是 |none|
|» image_path|body|string| 是 |none|
|» save_path|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 读取指定目录下文件的内容

POST /readFile

读取指定目录下文件的内容

> Body 请求参数

```json
{
  "path": "string",
  "filename": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» path|body|string| 是 |none|
|» filename|body|string| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 创建VNC服务

POST /vnc

创建VNC服务

> Body 请求参数

```json
{
  "vnc_file_path": "/home/software/vnc",
  "host": "192.168.36.155",
  "port": 59002,
  "username": "qimen",
  "password": "test123",
  "time": 7200,
  "vmid": 101
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» vnc_file_path|body|string| 是 |vnc token目录|
|» host|body|string| 是 |连接地址|
|» port|body|integer| 是 |端口|
|» username|body|string| 是 |vnc服务用户名|
|» password|body|string| 是 |vnc密码|
|» time|body|integer| 是 |有效时间，秒|
|» vmid|body|integer| 是 |虚拟机id|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 停止指定vnc服务

POST /vnc/stop

停止指定vnc服务

> Body 请求参数

```json
{
  "port": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» port|body|integer| 是 |none|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

## POST 导入vnc配置信息

POST /vnc/import

导入vnc配置信息

> Body 请求参数

```json
{
  "vnc_file_path": "string",
  "host": "string",
  "port": 0,
  "username": "string",
  "password": "string",
  "time": 0,
  "vmid": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 否 |none|
|body|body|object| 否 |none|
|» vnc_file_path|body|string| 是 |vnc token目录|
|» host|body|string| 是 |连接地址|
|» port|body|integer| 是 |端口|
|» username|body|string| 是 |vnc服务用户名|
|» password|body|string| 是 |vnc密码|
|» time|body|integer| 是 |有效时间，秒|
|» vmid|body|integer| 是 |虚拟机id|

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|成功|Inline|

### 返回数据结构

# 数据模型

<h2 id="tocS_VncInfo">VncInfo</h2>

<a id="schemavncinfo"></a>
<a id="schema_VncInfo"></a>
<a id="tocSvncinfo"></a>
<a id="tocsvncinfo"></a>

```json
{
  "id": 0,
  "hostId": 0,
  "vmid": 0,
  "host": "string",
  "port": 0,
  "username": "string",
  "password": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|hostId|integer|true|none||none|
|vmid|integer|true|none||none|
|host|string|true|none||none|
|port|integer|true|none||none|
|username|string|true|none||none|
|password|string|true|none||none|

<h2 id="tocS_【被控】VncParams">【被控】VncParams</h2>

<a id="schema【被控】vncparams"></a>
<a id="schema_【被控】VncParams"></a>
<a id="tocS【被控】vncparams"></a>
<a id="tocs【被控】vncparams"></a>

```json
{
  "vnc_file_path": "string",
  "host": "string",
  "port": 0,
  "username": "string",
  "password": "string",
  "time": 0,
  "vmid": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|vnc_file_path|string|true|none||vnc token目录|
|host|string|true|none||连接地址|
|port|integer|true|none||端口|
|username|string|true|none||vnc服务用户名|
|password|string|true|none||vnc密码|
|time|integer|true|none||有效时间，秒|
|vmid|integer|true|none||虚拟机id|

<h2 id="tocS_vncNode">vncNode</h2>

<a id="schemavncnode"></a>
<a id="schema_vncNode"></a>
<a id="tocSvncnode"></a>
<a id="tocsvncnode"></a>

```json
{
  "id": 0,
  "name": "string",
  "host": "string",
  "port": 0,
  "domain": "string",
  "protocol": 0,
  "status": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|name|string|true|none||none|
|host|string|true|none||none|
|port|integer|true|none||none|
|domain|string|true|none||对外公开的域名|
|protocol|integer¦null|false|none||ssl，默认0不开启|
|status|integer|true|none||none|

<h2 id="tocS_RenewalParams">RenewalParams</h2>

<a id="schemarenewalparams"></a>
<a id="schema_RenewalParams"></a>
<a id="tocSrenewalparams"></a>
<a id="tocsrenewalparams"></a>

```json
{
  "hostId": 0,
  "expirationTime": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|hostId|integer|true|none||虚拟机ID|
|expirationTime|integer|true|none||到期时间戳|

<h2 id="tocS_ConfigureTemplate">ConfigureTemplate</h2>

<a id="schemaconfiguretemplate"></a>
<a id="schema_ConfigureTemplate"></a>
<a id="tocSconfiguretemplate"></a>
<a id="tocsconfiguretemplate"></a>

```json
{
  "configureTemplateId": 0,
  "sockets": 0,
  "cores": 0,
  "threads": 0,
  "devirtualization": true,
  "kvm": true,
  "cpuModel": 0,
  "modelGroup": 0,
  "nested": true,
  "cpu": "string",
  "cpuUnits": 0,
  "bwlimit": 0,
  "arch": "string",
  "acpi": "string",
  "memory": 0,
  "storage": "string",
  "systemDiskSize": 0,
  "dataDisk": "string",
  "bandwidth": 0,
  "onBoot": 0,
  "name": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|configureTemplateId|integer¦null|false|none||配置模板ID|
|sockets|integer¦null|false|none||插槽数，默认为1|
|cores|integer¦null|false|none||核心，默认1|
|threads|integer¦null|false|none||线程数，默认1|
|devirtualization|boolean¦null|false|none||是否去虚拟化，默认false|
|kvm|boolean¦null|false|none||是否开启kvm虚拟化，默认开启|
|cpuModel|integer¦null|false|none||cpu信息模型ID|
|modelGroup|integer¦null|false|none||组合模型ID，优先级大于cpuModel|
|nested|boolean¦null|false|none||是否开启嵌套虚拟化，默认关闭|
|cpu|string¦null|false|none||cpu类型，默认kvm64，如果开启了nested，cpu必须为host或max|
|cpuUnits|integer¦null|false|none||cpu限制(单位:百分比)，列：10 为10%|
|bwlimit|integer¦null|false|none||I/O限制（单位MB/S）|
|arch|string¦null|false|none||系统架构(x86_64,arrch64)，默认x86_64|
|acpi|string¦null|false|none||acpi 默认1 开启|
|memory|integer¦null|false|none||内存 单位Mb，默认512|
|storage|string¦null|false|none||PVE磁盘名，auto为自动|
|systemDiskSize|integer¦null|false|none||系统盘大小，单位Gb|
|dataDisk|string|false|none||none|
|bandwidth|integer¦null|false|none||带宽，单位Mbps|
|onBoot|integer¦null|false|none||是否开机自启 0:否 1:是，默认0关闭|
|name|string|true|none||none|

<h2 id="tocS_reinstall">reinstall</h2>

<a id="schemareinstall"></a>
<a id="schema_reinstall"></a>
<a id="tocSreinstall"></a>
<a id="tocsreinstall"></a>

```json
{
  "hostId": 0,
  "os": "string",
  "newPassword": "string",
  "resetDataDisk": true
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|hostId|integer|true|none||hostId，非vmId|
|os|string|true|none||系统全面或系统id|
|newPassword|string¦null|true|none||新密码，为空不重置|
|resetDataDisk|boolean|true|none||是否重置数据盘|

<h2 id="tocS_ModelGroup">ModelGroup</h2>

<a id="schemamodelgroup"></a>
<a id="schema_ModelGroup"></a>
<a id="tocSmodelgroup"></a>
<a id="tocsmodelgroup"></a>

```json
{
  "id": 0,
  "cpuModel": 0,
  "smbiosModel": "string",
  "info": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|cpuModel|integer¦null|false|none||cpu模型id|
|smbiosModel|string¦null|false|none||smbios模型id集|
|info|string|true|none||备注|

<h2 id="tocS_SmBios">SmBios</h2>

<a id="schemasmbios"></a>
<a id="schema_SmBios"></a>
<a id="tocSsmbios"></a>
<a id="tocssmbios"></a>

```json
{
  "id": 0,
  "type": "string",
  "model": {},
  "info": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|type|string¦null|false|none||none|
|model|object|true|none||none|
|info|string|true|none||备注|

<h2 id="tocS_CpuInfo">CpuInfo</h2>

<a id="schemacpuinfo"></a>
<a id="schema_CpuInfo"></a>
<a id="tocScpuinfo"></a>
<a id="tocscpuinfo"></a>

```json
{
  "id": 0,
  "name": "string",
  "family": 0,
  "model": 0,
  "stepping": 0,
  "level": "string",
  "xlevel": "string",
  "vendor": "string",
  "l3Cache": true
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|name|string¦null|false|none||cpu名称|
|family|integer¦null|false|none||CPU系列|
|model|integer¦null|false|none||型号|
|stepping|integer¦null|false|none||步进|
|level|string¦null|false|none||CPU型号|
|xlevel|string¦null|false|none||CPU扩展型号|
|vendor|string¦null|false|none||厂商|
|l3Cache|boolean¦null|false|none||三缓|

<h2 id="tocS_Group">Group</h2>

<a id="schemagroup"></a>
<a id="schema_Group"></a>
<a id="tocSgroup"></a>
<a id="tocsgroup"></a>

```json
{
  "id": 0,
  "name": "string",
  "parent": 0,
  "realm": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|name|string|true|none||地区名|
|parent|integer¦null|false|none||父级节点id|
|realm|integer|true|none||目录级别，0为顶级，1为子级|

<h2 id="tocS_OS">OS</h2>

<a id="schemaos"></a>
<a id="schema_OS"></a>
<a id="tocSos"></a>
<a id="tocsos"></a>

```json
{
  "id": 0,
  "name": "string",
  "fileName": "string",
  "type": "string",
  "arch": "string",
  "osType": "string",
  "downType": 0,
  "url": "string",
  "size": "string",
  "path": "string",
  "cloud": 0,
  "status": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer|true|none||none|
|name|string|true|none||系统名称（别称）|
|fileName|string|true|none||镜像文件全名|
|type|string|true|none||镜像类型[windows，linux]|
|arch|string|true|none||镜像架构【x86_64，aarch64】|
|osType|string|true|none||镜像操作系统【centos,debian,ubuntu,alpine,fedora,opensuse,archlinux等】|
|downType|integer|true|none||0=url下载;1=手动上传，该字段禁止修改|
|url|string|true|none||none|
|size|string|true|none||镜像大小禁止修改|
|path|string|true|none||路径|
|cloud|integer|true|none||cloud-init【0=不使用;1=使用】|
|status|integer|true|none||0:正常 1:停用 2:异常|

<h2 id="tocS_VmParams">VmParams</h2>

<a id="schemavmparams"></a>
<a id="schema_VmParams"></a>
<a id="tocSvmparams"></a>
<a id="tocsvmparams"></a>

```json
{
  "nodeid": 0,
  "hostname": "string",
  "configureTemplateId": 0,
  "sockets": 0,
  "cores": 0,
  "threads": 0,
  "devirtualization": true,
  "kvm": true,
  "cpuModel": 0,
  "modelGroup": 0,
  "nested": true,
  "cpu": "string",
  "cpuUnits": 0,
  "bwlimit": 0,
  "arch": "string",
  "acpi": "string",
  "memory": 0,
  "storage": "string",
  "systemDiskSize": 0,
  "dataDisk": "string",
  "bridge": "string",
  "ipConfig": "string",
  "os": "string",
  "osType": "string",
  "bandwidth": 0,
  "onBoot": 0,
  "username": "string",
  "password": "string",
  "expirationTime": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|nodeid|integer|true|none||节点ID|
|hostname|string¦null|false|none||虚拟机名|
|configureTemplateId|integer¦null|false|none||配置模板ID|
|sockets|integer¦null|false|none||插槽数，默认为1|
|cores|integer¦null|false|none||核心，默认1|
|threads|integer¦null|false|none||线程数，默认1|
|devirtualization|boolean¦null|false|none||是否去虚拟化，默认false|
|kvm|boolean¦null|false|none||是否开启kvm虚拟化，默认开启|
|cpuModel|integer¦null|false|none||cpu信息模型ID|
|modelGroup|integer¦null|false|none||组合模型ID，优先级大于cpuModel|
|nested|boolean¦null|false|none||是否开启嵌套虚拟化，默认关闭|
|cpu|string¦null|false|none||cpu类型，默认kvm64，如果开启了nested，cpu必须为host或max|
|cpuUnits|integer¦null|false|none||cpu限制(单位:百分比)，列：10 为10%|
|bwlimit|integer¦null|false|none||I/O限制（单位MB/S）|
|arch|string¦null|false|none||系统架构(x86_64,arrch64)，默认x86_64|
|acpi|string¦null|false|none||acpi 默认1 开启|
|memory|integer¦null|false|none||内存 单位Mb，默认512|
|storage|string¦null|false|none||PVE磁盘名，auto为自动|
|systemDiskSize|integer¦null|false|none||系统盘大小，单位Gb|
|dataDisk|string|false|none||none|
|bridge|string¦null|false|none||网桥|
|ipConfig|string|false|none||none|
|os|string|true|none||操作系统，可填镜像名称或id|
|osType|string¦null|false|none||操作系统类型，（windows|linux）|
|bandwidth|integer¦null|false|none||带宽，单位Mbps|
|onBoot|integer¦null|false|none||是否开机自启 0:否 1:是，默认0关闭|
|username|string¦null|false|none||虚拟机登录用户名|
|password|string¦null|false|none||虚拟机登录密码|
|expirationTime|integer¦null|false|none||到期时间，时间戳|

