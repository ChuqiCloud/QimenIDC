<?php

use app\common\logic\RunMap;
use app\common\model\HostModel;
use think\Db;

#function qimenidc_idcsmartauthorize(){}

// 配置数据
function qimenidc_MetaData(){
	return [
		'DisplayName'=>'QimenIDC', 
		'APIVersion'=>'1.1', 
		'HelpDoc'=>'https://www.idcsmart.com/wiki_list/339.html#3.3',
		'version'=>'1.0.1'
	];
}

function qimenidc_ConfigOptions(){
	return [
		[
			'type'=>'text', 
			'name'=>'数据中心', 
			'description'=>'所属数据中心位置',
			'key'=>'Location'
		],
		[
			'type'=>'text', 
			'name'=>'节点ID', 
			'description'=>'ID(必填)',
			'key'=>'nodeid',
		],
		[
			'type'=>'text', 
			'name'=>'配置模板ID', 
			'description'=>'配置模板ID,默认0不使用配置模板',
			'default'=>'0',
			'key'=>'configureTemplateId',
		],
		[
			'type'=>'text', 
			'name'=>'内存', 
			'description'=>'MB',
			'default'=>'1024',
			'key'=>'memory',
		],
		[
			'type'=>'text', 
			'name'=>'带宽', 
			'description'=>'Mbps',
			'default'=>'1',
			'key'=>'bandwidth',
		],
		[
			'type'=>'text', 
			'name'=>'备份迁移I/O限制',
			'description'=>'MB/s',
			'default'=>'200',
			'key'=>'bwlimit',
		],
		[
			'type'=>'text', 
			'name'=>'流量', 
			'description'=>'GB(0为不限制)',
			'default'=>'0',
			'key'=>'flow_limit',
		],
		[
			'type'=>'text', 
			'name'=>'IP数量', 
			'description'=>'默认分配公网IP数量',
			'default'=>'1',
			'key'=>'ipv4',
		],
		[
			'type'=>'text', 
			'name'=>'默认镜像', 
			'description'=>'可填镜像ID或者镜像名称,如果设置可选配置则优先可选配置',
			'key'=>'os',
		],
		[
			'type'=>'text', 
			'name'=>'插槽数', 
			'description'=>'CPU插槽数',
			'default'=>'1',
			'key'=>'sockets',
		],
		[
			'type'=>'text', 
			'name'=>'核心数', 
			'description'=>'核心',
			'default'=>'2',
			'key'=>'cores',
		],
		[
			'type'=>'text', 
			'name'=>'线程数', 
			'description'=>'线程数',
			'default'=>'1',
			'key'=>'threads',
		],
		[
			'type'=>'text', 
			'name'=>'系统硬盘大小', 
			'description'=>'GB',
			'default'=>'40',
			'key'=>'SystemDisk',
		],
		[
			'type'=>'text', 
			'name'=>'CPU类型', 
			'description'=>'如:kvm64,host,注意开启嵌套虚拟化后,此处必须填写host',
			'default'=>'kvm64',
			'key'=>'cpu_mode',
		],
		[
			'type'=>'dropdown', 
			'name'=>'嵌套虚拟化', 
			'description'=>'是否开启嵌套虚拟化',
			'options'=>[
				'false'=>'关闭', 
				'true'=>'开启'
			], 
			'default'=>'false',
			'key'=>'nested',
		],
		[
			'type'=>'dropdown', 
			'name'=>'KVM硬件虚拟化', 
			'description'=>'是否开启KVM硬件虚拟化加速（建议开启加速，且开启CPU类型才能填host）',
			'options'=>[
				'false'=>'关闭', 
				'true'=>'开启'
			], 
			'default'=>'true',
			'key'=>'kvm',
		],
		[
			'type'=>'dropdown', 
			'name'=>'去虚拟化机化', 
			'description'=>'是否去虚拟机化',
			'options'=>[
				'false'=>'关闭', 
				'true'=>'开启'
			], 
			'default'=>'false',
			'key'=>'devirtualization',
		],
		[
			'type'=>'text', 
			'name'=>'CPU限制', 
			'description'=>'%',
			'default'=>'70',
			'key'=>'cpuUnits',
		],
		[
			'type'=>'text', 
			'name'=>'组合模型ID', 
			'description'=>'组合型产品ID,默认0不使用,优先级比CPU模型高',
			'default'=>'0',
			'key'=>'modelGroup',
		],
		[
			'type'=>'text', 
			'name'=>'VNC控制器名称', 
			'description'=>'指定VNC控制器,该项为后台控制器名称,默认0为不指定',
			'default'=>'0',
			'key'=>'vncKey',
		],
		[
			'type'=>'dropdown', 
			'name'=>'NAT', 
			'description'=>'是否为NAT产品',
			'options'=>[
				'0'=>'否', 
				'1'=>'是'
			],
			'default'=>'0',
			'key'=>'nat',
		],
		[
			'type'=>'text', 
			'name'=>'NAT数量', 
			'description'=>'NAT转发端口数量',
			'default'=>'0',
			'key'=>'natnum',
		],
		[
			'type'=>'text', 
			'name'=>'cpu模型ID', 
			'description'=>'CPU模型ID,默认0不使用CPU模型',
			'default'=>'0',
			'key'=>'cpuModel',
		],
		[
			'type'=>'text', 
			'name'=>'流量重置日', 
			'description'=>'默认0开通日 1每月1号',
			'default'=>'0',
			'key'=>'resetFlowTime',
		],
		[
			'type'=>'text', 
			'name'=>'超流操作', 
			'description'=>'默认0停机 若填128表示限速128单位kb',
			'default'=>'0',
			'key'=>'outFlow',
		]
	];
}

// 连接测试
function qimenidc_TestLink($params){
	$url = qimenidc_GetUrl($params, '/api/v1/status', $sign);
	
	$res = qimenidc_Curl($params,$url, [], 10, 'GET');
	if(isset($res['code']) && $res['code'] == 20000){
		$result['status'] = 200;
		$result['data']['server_status'] = 1;
	}else{
		$result['status'] = 200;
		$result['data']['server_status'] = 0;
		$result['data']['msg'] = $res['message'];
	}
	return $result;
}

// 图表
function qimenidc_Chart(){
	return [
		'cpu'=>[
			'title'=>'CPU使用量',
		],
		'memory'=>[
			'title'=>'内存用量',
		],
		'disk'=>[
			'title'=>'磁盘IO',
		],
		'flow'=>[
			'title'=>'网卡'
		],
	];
}

// 图表数据
function qimenidc_ChartData($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'数据获取失败'];
    }
    // 先同步最新流量数据，消除滞后
    qimenidc_Sync($params);
    // 拼接请求参数数组
    $query = [
        'hostId'=>$vserverid,
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/getVmInfo', $query);

    $res = qimenidc_Curl($params,$url, [], 30, 'GET');
    // var_dump($res);
    if(isset($res['code']) && $res['code'] == 20000){

        $start = $params['chart']['start']/1000;

        $result['status'] = 'success';
        $result['data'] = [];
        if(isset($params['chart']['type']) && $params['chart']['type'] == 'cpu'){

            $result['data']['unit'] = '%';
            $result['data']['chart_type'] = 'area';
            $result['data']['list'] = [];
            $result['data']['label'] = ['CPU使用率(%)'];
            foreach($res['data']['rrddata']['data'] as $v){
                $time = $v['time'];
                if($time < $start){
                    continue;
                }
				// 判断是否存在cpu字段
				if(!isset($v['cpu'])){
					continue;
				}
				$cpu = $v['cpu'];
				// 原始值为小数(如0.1686), *100转为百分比, 保留两位小数
				$cpu = round($cpu * 100, 2);
                $result['data']['list'][0][] = [
                    'time'=>date('Y-m-d H:i:s', $time),
                    'value'=>$cpu
                ]; 
            }
        }elseif (isset($params['chart']['type']) && $params['chart']['type'] == 'memory'){

			$result['data']['unit'] = 'GB';
			$result['data']['chart_type'] = 'bar';
			$result['data']['list'] = [];
			$result['data']['label'] = ['总量(GB)','已用(GB)'];
			foreach($res['data']['rrddata']['data'] as $v){
				$time = $v['time'];
				if($time < $start){
					continue;
				}
				// 判断是否存在memory字段
				if(!isset($v['mem'])){
					continue;
				}
				$max = $v['maxmem'];
				$memory = $v['mem'];
				// 转换为GB
				$max = $max/1024/1024/1024;
				$memory = $memory/1024/1024/1024;
				// 保留两位小数
				$max = round($max, 2);
				$memory = round($memory, 2);
				$result['data']['list'][0][] = [
					'time'=>date('Y-m-d H:i:s', $time),
					'value'=>$max
				];
				$result['data']['list'][1][] = [
					'time'=>date('Y-m-d H:i:s', $time),
					'value'=>$memory
				];
			}
		}elseif (isset($params['chart']['type']) && $params['chart']['type'] == 'disk'){

            $result['data']['chart_type'] = 'line';
            $result['data']['list'] = [];

            // 先收集所有数据，用KB/s计算
            $rawData = [];
            foreach($res['data']['rrddata']['data'] as $v){
                $time = $v['time'];
                if($time < $start){
                    continue;
                }
				if(!isset($v['diskread']) || !isset($v['diskwrite'])){
					continue;
				}
				$rawData[] = [
					'time' => $time,
					'diskread' => $v['diskread'] / 1024,   // bytes/s -> KB/s
					'diskwrite' => $v['diskwrite'] / 1024,
				];
            }

			// 找最大值，>=1024KB/s则用MB/s
			$maxVal = 0;
			foreach($rawData as $d){
				if($d['diskread'] > $maxVal) $maxVal = $d['diskread'];
				if($d['diskwrite'] > $maxVal) $maxVal = $d['diskwrite'];
			}
			if($maxVal >= 1024){
				$result['data']['unit'] = 'MB/s';
				$result['data']['label'] = ['读取速度(MB/s)','写入速度(MB/s)'];
				$divisor = 1024;
			}else{
				$result['data']['unit'] = 'KB/s';
				$result['data']['label'] = ['读取速度(KB/s)','写入速度(KB/s)'];
				$divisor = 1;
			}

			foreach($rawData as $d){
				$diskread = round($d['diskread'] / $divisor, 2);
				$diskwrite = round($d['diskwrite'] / $divisor, 2);
				$date = date('Y-m-d H:i:s', $d['time']);
				$result['data']['list'][0][] = [
					'time'=>$date,
					'value'=>$diskread
				];
				$result['data']['list'][1][] = [
					'time'=>$date,
					'value'=>$diskwrite
				];
			}
        } elseif (isset($params['chart']['type']) && $params['chart']['type'] == 'flow'){

            $result['data']['chart_type'] = 'line';
            $result['data']['list'] = [];

            // 先收集所有数据，用Kbps计算
            $rawData = [];
            foreach($res['data']['rrddata']['data'] as $v){
                $time = $v['time'];
                if($time < $start){
                    continue;
                }
				if(!isset($v['netin']) || !isset($v['netout'])){
					continue;
				}
				$rawData[] = [
					'time' => $time,
					'netin' => $v['netin'] * 8 / 1024,   // bytes/s -> Kbps
					'netout' => $v['netout'] * 8 / 1024,
				];
            }

			// 找最大值，>=1024Kbps则用Mbps
			$maxVal = 0;
			foreach($rawData as $d){
				if($d['netin'] > $maxVal) $maxVal = $d['netin'];
				if($d['netout'] > $maxVal) $maxVal = $d['netout'];
			}
			if($maxVal >= 1024){
				$result['data']['unit'] = 'Mbps';
				$result['data']['label'] = ['进(Mbps)','出(Mbps)'];
				$divisor = 1024;
			}else{
				$result['data']['unit'] = 'Kbps';
				$result['data']['label'] = ['进(Kbps)','出(Kbps)'];
				$divisor = 1;
			}

			foreach($rawData as $d){
				$netin = round($d['netin'] / $divisor, 2);
				$netout = round($d['netout'] / $divisor, 2);
				$date = date('Y-m-d H:i:s', $d['time']);
				$result['data']['list'][0][] = [
					'time'=>$date,
					'value'=>$netin
				];
				$result['data']['list'][1][] = [
					'time'=>$date,
					'value'=>$netout
				];
			}
        }
        return $result;
    } else {
        return ['status'=>'error', 'msg'=>'数据获取失败'];
    }
}

// 标准输出
function qimenidc_ClientArea($params){
	if($params['configoptions']['nat']==1){
		$panel = [
			'snapshot'=>[
				'name'=>'快照',
			],
			'backups'=>[
				'name'=>'备份',
			],
			'security_group'=>[
				'name'=>'安全组',
			],
			'cd_rom'=>[
				'name'=>'光驱',
			],
			'nat_acl'=>[
				'name'=>'NAT转发',
			],
			'nat_web'=>[
				'name'=>'NAT建站',
			]
		];
	}else{
		$panel = [
			'snapshot'=>[
				'name'=>'快照',
			],
			'backups'=>[
				'name'=>'备份',
			],
			'security_group'=>[
				'name'=>'安全组',
			],
			'cd_rom'=>[
				'name'=>'光驱',
			]
		];
	}
	#unset($panel['nat_acl']);
	unset($panel['nat_web']);
	unset($panel['cd_rom']);
	return $panel;
}

// 输出内容
function qimenidc_ClientAreaOutput($params, $key){
    qimenidc_Sync($params);
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return '';
	}
	if($key == 'snapshot'){
		$snapshots = [];
		$res = qimenidc_getSnapshots($params);
		if(isset($res['code']) && $res['code'] == 20000 && isset($res['data']['data']) && is_array($res['data']['data'])){
			$snapshots = $res['data']['data'];
		}
		$snapshot_count = qimenidc_countSnapshots($snapshots);
		$snapshot_limit = qimenidc_parseFeatureLimit($params, 'snap_num');
		
		return [
			'template'=>'templates/snapshot.html',
			'vars'=>[
				'list'=>$snapshots,
				'snapshot_count'=>$snapshot_count,
				'snapshot_limit'=>$snapshot_limit,
				'snapshot_supported'=>$snapshot_limit >= 0,
				'snapshot_can_create'=>$snapshot_limit == 0 || ($snapshot_limit > 0 && $snapshot_count < $snapshot_limit),
			]
		];
	}else if($key == 'security_group'){
		$security_groups = [];
		$used_security_group = 0;
		$res = qimenidc_getSecurityGroups($params);
		if(isset($res['code']) && $res['code'] == 20000){
			$security_groups = qimenidc_normalizeSecurityGroups($res);
			$security_groups = qimenidc_fillSecurityGroupApplyStatus($params, $security_groups);
			foreach($security_groups as $group){
				if(!empty($group['used'])){
					$used_security_group = $group['id'];
					break;
				}
			}
		}

		return [
			'template'=>'templates/security_group.html',
			'vars'=>[
				'list'=>$security_groups,
				'used'=>$used_security_group,
			]
		];
	}else if($key == 'backups'){
		$backups = [];
		$res = qimenidc_getBackups($params);
		if(isset($res['code']) && $res['code'] == 20000 && isset($res['data']['data']) && is_array($res['data']['data'])){
			$backups = $res['data']['data'];
		}
		$backup_count = count($backups);
		$backup_limit = qimenidc_parseFeatureLimit($params, 'backup_num');

		return [
			'template'=>'templates/backups.html',
			'vars'=>[
				'list'=>$backups,
				'backup_count'=>$backup_count,
				'backup_limit'=>$backup_limit,
				'backup_supported'=>$backup_limit >= 0,
				'backup_can_create'=>$backup_limit == 0 || ($backup_limit > 0 && $backup_count < $backup_limit),
			]
		];
	}else if($key == 'cd_rom'){
		// 获取安全组列表

		return [
			'template'=>'templates/cd_rom.html',
			'vars'=>[
				'list'=>[],
				'host'=>[]
			]
		];
	}else if($key == 'nat_acl'){
		// 获取策略列表
		$NatVm = qimenidc_getNatVm($params)["data"];
		return [
			'template'=>'templates/nat_acl.html',
			'vars'=>[
				'list'=> $NatVm,
				'natInfo'=> qimenidc_getNatInfo($params)["data"],
				'natnum'=>$params['configoptions']['natnum'],
				'natuse'=>count($NatVm)
			]
		];
	}else if($key == 'nat_web'){

		return [
			'template'=>'templates/nat_web.html',
			'vars'=>[
				'list'=>[]
			]
		];
	}
}

// 开通
function qimenidc_CreateAccount($params){
	// 获取自定义字段
	$vserverid = qimenidc_GetServerid($params);
	if(!empty($vserverid)){
		return '已开通,不能重复开通';
	}
	if(empty($params['password'])){
		$sys_pwd = randStr(8);
	}else{
		$sys_pwd = $params['password'];
	}
	$vnc_pwd = randStr(8);

	$post_data = [];
	$post_data['nodeid'] = $params['configoptions']['nodeid']; // 节点ID
	$post_data['cores'] = $params['configoptions']['cores']; // CPU核心数
	$post_data['cpu'] = $params['configoptions']['cpu_mode']; // CPU模式
	$post_data['memory'] = $params['configoptions']['memory']; // 内存大小
	$post_data['systemDiskSize'] = $params['configoptions']['SystemDisk']; // 系统盘大小
	$post_data['bandwidth'] = $params['configoptions']['bandwidth']; // 带宽
	$post_data['sockets'] = $params['configoptions']['sockets']; // CPU插槽数
	$post_data['bwlimit'] = $params['configoptions']['bwlimit']; // I/O限制
	$post_data['threads'] = $params['configoptions']['threads']; // CPU线程数
	$post_data['flowLimit'] = $params['configoptions']['flow_limit']; // 流量限制
	$post_data['kvm'] = $params['configoptions']['kvm']; // KVM
	$post_data['devirtualization'] = $params['configoptions']['devirtualization']; // 去虚拟机化
	$post_data['cpuUnits'] = $params['configoptions']['cpuUnits']; // CPU权重
	if($params['configoptions']['cpuModel'] != 0){
		$post_data['cpuModel'] = $params['configoptions']['cpuModel']; // CPU模型ID
	}
	if($params['configoptions']['modelGroup'] != 0){
		$post_data['modelGroup'] = $params['configoptions']['modelGroup']; // 组合模型ID
	}
	if($params['configoptions']['configureTemplateId'] != 0){
		$post_data['configureTemplateId'] = $params['configoptions']['configureTemplateId']; // 配置模板ID
	}
	$post_data['nested'] = $params['configoptions']['nested']; // 嵌套虚拟化
	$post_data['os'] = $params['configoptions']['os']; // 操作系统
	$post_data['osname'] = $params['configoptions']['os'] . '.qcow2'; // 操作系统
	$post_data['ifnat'] = $params['configoptions']['nat']; // 是否为NAT产品
	$post_data['natnum'] = $params['configoptions']['natnum']; // NAT端口数量
	$post_data['resetFlowTime'] = $params['configoptions']['resetFlowTime']; // 重置流量时间
	$post_data['outFlow'] = $params['configoptions']['outFlow']; // 超流操作
	$post_data['networkType'] = $params['configoptions']['networkType']; // 网络类型 默认经典
	$post_data['vpcSubnetId'] = $params['configoptions']['vpcSubnetId']; // VPC子网ID
	$post_data['ipv4num'] = $params['configoptions']['ipv4num']; // IPV4数量
	$post_data['ipv6num'] = $params['configoptions']['ipv6num']; // IPV6数量 默认0
	$post_data['flowType'] = $params['configoptions']['flowType']; // 流量计费类型 in(仅计入) out(仅计出) in+out(双向)
	$post_data['onBoot'] = 1; // 自启，默认开
	// 如果$params['domain']不为空,则使用$params['domain']作为主机名,否则不传递主机名参数
	if(!empty($params['domain'])){
		$post_data['hostname'] = $params['domain']; // 主机名
	}

	$post_data['password'] = $sys_pwd;

	$url = qimenidc_GetUrl($params, '/api/v1/pve/cerateVM');
	
	$res = qimenidc_Curl($params,$url, $post_data);

	if(isset($res['code']) && $res['code'] == 20000){
		// 存入产品自定义字段
		$customid = Db::name('customfields')
					->where('type', 'product')
					->where('relid', $params['productid'])
					->where('fieldname', 'vserverid')
					->value('id');
		if(empty($customid)){
			// 添加自定义字段
			$customfields = [
				'type'=>'product',
				'relid'=>$params['productid'],
				'fieldname'=>'vserverid',
				'fieldtype'=>'text',
				'adminonly'=>1,
				'create_time'=>time()
			];
			$customid = Db::name('customfields')->insertGetId($customfields);
		}
		$exist = Db::name('customfieldsvalues')
				->where('fieldid', $customid)
				->where('relid', $params['hostid'])
				->find();
		if(empty($exist)){
			$data = [
				'fieldid'=>$customid,
				'relid'=>$params['hostid'],
				'value'=>$res['data']['hostid'],
				'create_time'=>time()
			];
			Db::name('customfieldsvalues')->insert($data);
		}else{
			Db::name('customfieldsvalues')->where('id', $exist['id'])->update(['value'=>$res['data']['hostid']]);
		}

		// {"code":20000,"message":"请求成功","data":{"vmhost": {"ipData":[{"ip":"192.168.36.3","subnetMask":24,"gateway":"192.168.36.2"}]}}}

		// 存入IP
		$mainip = '';
		$ip = [];

		foreach($res['data']['ipData'] as $v){
			// 判断ip数量是否大于1
			if(count($res['data']['ipData']) > 1){
				// 判断是否为第一个ip
				if($res['data']['ipData'][0]['ip'] == $v['ip']){
					$mainip = $v['ip'];
				}else{
					$ip[] = $v['ip'];
				}

			}
			// ip数量等于1
			else{
				$mainip = $v['ip'];
			}
		}


		// 获取当前操作系统
		$os_info = Db::name('host_config_options')
                    ->alias('a')
                    ->field('c.option_name')
                    ->leftJoin('product_config_options b', 'a.configid=b.id')
                    ->leftJoin('product_config_options_sub c', 'a.optionid=c.id')
                    ->where('a.relid', $params['hostid'])
                    ->where('b.option_type', 5)
                    ->find();
        // if(stripos($os_info['option_name'], 'win') !== false){
        //     $username = 'administrator';
        // }else{
        //     $username = 'root';
        // }
		$username = $res['data']['username'];
		$update['dedicatedip'] = $mainip;
		$update['assignedips'] = implode(',', $ip);
		// 存入服务器密码
		$update['domainstatus'] = 'Active';
		$update['username'] = $username;
		$update['password'] = cmf_encrypt($res['data']['password']);
		$update['domain'] = $res['data']['hostname'];
		if(empty($os_info)){
			$update['os'] = $post_data['os'];
		}
  		$update['bwlimit'] = (int)$params['configoptions']['flow_limit'];
		Db::name('host')->where('id', $params['hostid'])->update($update);
		return 'ok';
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '开通失败'];
	}
}

// 暂停
function qimenidc_SuspendAccount($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/pause');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '暂停失败'];
	}
}

// 解除暂停
function qimenidc_UnsuspendAccount($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/unpause');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '解除暂停失败'];
	}
}

// 删除
function qimenidc_TerminateAccount($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	$url = qimenidc_GetUrl($params, '/api/v1/pve/delete/'.$vserverid);
	
	$res = qimenidc_Curl($params,$url, [], 30, 'DELETE');
	if(isset($res['code']) && $res['code'] == 20000){
		// 删除成功后
		$customid = Db::name('customfields')
					->where('type', 'product')
					->where('relid', $params['productid'])
					->where('fieldname', 'vserverid')
					->value('id');
		Db::name('customfieldsvalues')->where('fieldid', $customid)->where('relid', $params['hostid'])->delete();
		// Db::name('host')->where('id', $params['hostid'])->update(['dedicatedip'=>'','assignedips'=>'','password'=>'']);
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '删除失败'];
	}
}

// 开机
function qimenidc_On($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/start');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '开机失败'];
	}
}

// 关机
function qimenidc_Off($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/stop');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '关机失败'];
	}
}

// 重启
function qimenidc_Reboot($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/reboot');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '关机失败'];
	}
}

// 硬关机
function qimenidc_HardOff($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/shutdown');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '关机失败'];
	}
}

// 硬重启
function qimenidc_HardReboot($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/power/'.$vserverid.'/reboot');
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '关机失败'];
	}
}

// 重置流量
function qimenidc_resetFlow($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	$url = qimenidc_GetUrl($params, '/api/v1/pve/resetVmHostFlow/'.$vserverid);
	
	$res = qimenidc_Curl($params,$url, [], 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '重置失败'];
	}
}
// 升降级
function qimenidc_ChangePackage($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
    $post_data = [];
    $res = null;
	if(isset($params['configoptions_upgrade']['CPU'])){
		$post_data['cores'] = $params['configoptions']['CPU'];
	}
	if(isset($params['configoptions_upgrade']['Memory'])){
		$post_data['memory'] = $params['configoptions']['memory'];
	}
	if(isset($params['configoptions_upgrade']['Disk Space'])){
		$post_data['systemDiskSize'] = $params['configoptions']['Disk Space'];
	}
	if(isset($params['configoptions_upgrade']['Network Speed'])){
		$post_data['bandwidth'] = $params['configoptions']['Network Speed'] ?? $params['configoptions']['net_out'];
	}
	if(isset($params['configoptions_upgrade']['flow_limit'])){
        $post_data['flowLimit'] = $params['configoptions']['flow_limit'];
        
	}
	if(isset($params['configoptions_upgrade']['Extra IP Address'])){
		// $post_data['ip_num'] = $params['configoptions']['Extra IP Address'];
		// 没写IP数量
	}
	if(!empty($post_data)){
		$post_data['hostid'] = $vserverid;
		// 升级配置
		$url = qimenidc_GetUrl($params, '/api/v1/pve/updateVm');
		$res = qimenidc_Curl($params, $url, $post_data, 30, 'PUT');
		if(!qimenidc_apiSuccess($res)){
			return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '修改配置失败')];
		}
		$sync = qimenidc_Sync($params);
		if(!is_array($sync) || ($sync['status'] ?? '') !== 'success'){
			return ['status'=>'error', 'msg'=>is_array($sync) ? ($sync['msg'] ?? '修改后同步失败') : '修改后同步失败'];
		}
	}
	
	if($res === null){
		return ['status'=>'success', 'msg'=>'没有需要修改的配置'];
	}
	if(qimenidc_apiSuccess($res)){ # 配置改变才变
		$result['status'] = 'success';
		$result['msg'] = $res['message'] ?: '修改配置成功';
	}else{
		$result['status'] = 'error';
		$result['msg'] = $res['message'] ?: '修改配置失败';
	}
    return $result;
}
/**
 * @Author: 星禾
 * @Description: 按后端基础流量重置规则计算当前流量周期
 * @DateTime: 2026/10/14 19:59
 */
function qimenidc_FlowCycleWindow($vmhost, $now = null){
	if(!is_array($vmhost) || !array_key_exists('resetFlowTime', $vmhost)
		|| !in_array($vmhost['resetFlowTime'], [0, 1, '0', '1'], true)){
		return false;
	}
	$create_millis = $vmhost['createTime'] ?? null;
	if((!is_int($create_millis) && !is_string($create_millis))
		|| !ctype_digit((string)$create_millis) || $create_millis <= 0
		|| $create_millis > PHP_INT_MAX){
		return false;
	}
	$now = $now === null ? time() : $now;
	if(!is_int($now) || $now <= 0 || $create_millis > $now * 1000){
		return false;
	}
	$timezone = new \DateTimeZone(date_default_timezone_get());
	$created_at = intdiv((int)$create_millis, 1000);
	$created = (new \DateTimeImmutable('@'.$created_at))->setTimezone($timezone);
	$current = (new \DateTimeImmutable('@'.$now))->setTimezone($timezone);
	$month = $current->modify('first day of this month')->setTime(0, 0);
	if((int)$vmhost['resetFlowTime'] === 1){
		return [max($month->getTimestamp(), $created_at), $month->modify('+1 month')->getTimestamp()];
	}

	// 每个月都从原始开通日计算，避免 1 月 31 日逐月偏移成 28 日。
	$opening_day = (int)$created->format('j');
	$reset_on = function($target_month) use ($opening_day){
		$valid_day = min($opening_day, (int)$target_month->format('t'));
		return $target_month->setDate((int)$target_month->format('Y'),
			(int)$target_month->format('n'), $valid_day)->setTime(0, 0);
	};
	$first_reset = $reset_on($created->modify('first day of next month')->setTime(0, 0));
	if($now < $first_reset->getTimestamp()){
		return [$created_at, $first_reset->getTimestamp()];
	}
	$start = $reset_on($month);
	if($start->getTimestamp() > $now){
		$month = $month->modify('-1 month');
		$start = $reset_on($month);
	}
	return [$start->getTimestamp(), $reset_on($month->modify('+1 month'))->getTimestamp()];
}

/**
 * @Author: 星禾
 * @Description: 查询当前基础流量周期内已付款的流量包总额，单位 GB
 * @DateTime: 2026/10/14 20:20
 */
function qimenidc_FlowCycleCapacity($params, $vmhost, $now = null){
	$window = qimenidc_FlowCycleWindow($vmhost, $now);
	if($window === false){
		return false;
	}
	return (int)Db::name('dcim_buy_record')
		->where('type', 'flow_packet')
		->where('hostid', $params['hostid'])
		->where('uid', $params['uid'])
		->where('status', 1)
		->where('show_status', 0)
		->where('pay_time', '>=', $window[0])
		->where('pay_time', '<', $window[1])
		->sum('capacity');
}

// 流量包跟随基础额度重置；传当前周期总额，付款回调重试不会重复加流量。
function qimenidc_FlowPacketPaid($params, $now = null){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return false;
	}
	$info_url = qimenidc_GetUrl($params, '/api/v1/pve/getVmInfo', ['hostId'=>$vserverid]);
	$info = qimenidc_Curl($params, $info_url, [], 30, 'GET');
	if(!qimenidc_apiSuccess($info)){
		return false;
	}
	$capacity = qimenidc_FlowCycleCapacity($params, $info['data']['vmhost'] ?? null, $now);
	if($capacity === false){
		return false;
	}
	$post_data = [
		'hostid'=>$vserverid,
		'extraFlowLimit'=>$capacity,
	];
	$url = qimenidc_GetUrl($params, '/api/v1/pve/updateVm');
	$res = qimenidc_Curl($params, $url, $post_data, 30, 'PUT');
	if(!qimenidc_apiSuccess($res)){
		return false;
	}
	$sync = qimenidc_Sync($params);
	return is_array($sync) && ($sync['status'] ?? '') === 'success';
}
// Vnc
function qimenidc_Vnc($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
	// 设置请求参数
	$query = [
		'hostId'=>$vserverid,
		'page'=>1,
		'size'=>1000000,
	];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/getVnc', $query);

    $res = qimenidc_Curl($params,$url, [], 15, 'GET');
    if(isset($res['code']) && $res['code'] == 20000){
		/**
		 * 改版之后的接口
		 */
		
		$records = $res['data']['records'];
		$vnc_url = '';
		if($params['configoptions']['vncKey'] == 0){
			// 遍历records数组获取url，获取第一个后直接跳出
			foreach($records as $json){
				foreach($json as $k=>$v){
					$vnc_url = $v;
					break;
				}
			}
		}else{
			//$vnc_url = $res['data']['records'][$params['configoptions']['vncKey']];
			// 遍历records数组获取url，获取键为$params['configoptions']['vncKey']的url
			foreach($records as $json){
				foreach($json as $k=>$v){
					if($k == $params['configoptions']['vncKey']){
						$vnc_url = $v;
						break;
					}
				}
			}
		}
		// vnc地址判空
		if(empty($vnc_url)){
			return ['status'=>'error', 'msg'=>'VNC获取失败'];
		}
		$result['status'] = 'success';
		$result['msg'] = 'vnc获取成功';
		$result['url'] = $vnc_url;
    }else{
        // 当系统没有创建vnc时，第一次请求必失败且会创建一个vnc进程
        // 此时需要第二次获取才可以拿到vnc地址
        $res = qimenidc_Curl($params,$url, [], 15, 'GET');
        if(isset($res['code']) && $res['code'] == 20000){
		    $records = $res['data']['records'];
		    $vnc_url = '';
		    if($params['configoptions']['vncKey'] == 0){
		    	// 遍历records数组获取url，获取第一个后直接跳出
		    	foreach($records as $json){
			    	foreach($json as $k=>$v){
				    	$vnc_url = $v;
					    break;
				    }
			    }
		    }else{
			    //$vnc_url = $res['data']['records'][$params['configoptions']['vncKey']];
			    // 遍历records数组获取url，获取键为$params['configoptions']['vncKey']的url
			    foreach($records as $json){
				    foreach($json as $k=>$v){
					    if($k == $params['configoptions']['vncKey']){
						    $vnc_url = $v;
						    break;
					    }
				    }
			    }
		    }
		    // vnc地址判空
		    if(empty($vnc_url)){
			    return ['status'=>'error', 'msg'=>'VNC获取失败'];
		    }
		    $result['status'] = 'success';
		    $result['msg'] = 'vnc获取成功';
		    $result['url'] = $vnc_url;
        }else{
            $result['status'] = 'error';
            $result['msg'] = 'VNC获取失败';
        }
    }
    return $result;
}

// 重装系统
function qimenidc_Reinstall($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	//$os = input('post.os', 0, 'intval');
	if(empty($params['reinstall_os'])){
		return '操作系统错误';
	}
	// 判断是否是可配置选项
	// $r = Db::name('product_config_options_sub')
	// 	->alias('a')
	// 	->field('a.*')
	// 	->leftJoin('product_config_options b', 'a.config_id=b.id')
	// 	->leftJoin('product_config_links c', 'b.gid=c.gid')
	// 	->where('c.pid', $params['productid'])
	// 	->where('a.id', $os)
	// 	->find();
	// if(empty($r)){
	// 	return '操作系统错误';
	// }
	// $arr = explode('|', $r['option_name']);
	//return ['status'=>'error', 'msg'=>$params['reinstall_os_name']];
	$url = qimenidc_GetUrl($params, '/api/v1/pve/reinstall');

	// 设置body
	$put_data = [
		'hostId'=>$vserverid,
		'os'=>$params['reinstall_os'],
		'resetDataDisk'=> false,
	];

	$res = qimenidc_Curl($params,$url, $put_data, 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		if(stripos($params['reinstall_os_name'], 'win') !== false || stripos($params['reinstall_os_name'], 'Palworld') !== false){
        	$username = 'administrator';
    	}else{
        	$username = 'root';
    	}
    	Db::name('host')->where('id', $params['hostid'])->update(['username'=>$username]);
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '重装失败'];
	}
}

// 破解密码
function qimenidc_CrackPassword($params, $new_pass){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	$url = qimenidc_GetUrl($params, '/api/v1/pve/updateVmConfig/restPassword');

	$post_data['hostId'] = $vserverid;
	$post_data['newPassword'] = $new_pass;

	$res = qimenidc_Curl($params,$url, $post_data, 30, 'PUT');
	if(isset($res['code']) && $res['code'] == 20000){
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '同步失败'];
	}
}

// 同步
function qimenidc_Sync($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}

	// 设置请求参数
	$query = [
		'hostId'=>$vserverid,
	];

	$url = qimenidc_GetUrl($params, '/api/v1/pve/getVmInfo', $query);

	$res = qimenidc_Curl($params,$url, [], 30, 'GET');
	if(qimenidc_apiSuccess($res) && isset($res['data']['vmhost']) && is_array($res['data']['vmhost'])){
		$vmhost = $res['data']['vmhost'];
		if(!isset($vmhost['ipData']) || !is_array($vmhost['ipData'])){
			return ['status'=>'error', 'msg'=>'接口返回的主机信息不完整'];
		}
		foreach(['password', 'hostname', 'username', 'usedFlow'] as $field){
			if(!array_key_exists($field, $vmhost)){
				return ['status'=>'error', 'msg'=>'接口返回的主机信息不完整'];
			}
		}
		// 存入IP
		$mainip = '';
		$ip = [];

		foreach($res['data']['vmhost']['ipData'] as $v){
			// 判断ip数量是否大于1
			if(count($res['data']['vmhost']['ipData']) > 1){
				// 判断是否为第一个ip
				if($res['data']['vmhost']['ipData'][0]['ip'] == $v['ip']){
					$mainip = $v['ip'];
				}else{
					$ip[] = $v['ip'];
				}
			}
			// ip数量等于1
			else{
				$mainip = $v['ip'];
			}
		}
		$update['dedicatedip'] = $mainip;
		$update['assignedips'] = implode(',', $ip);
		$update['password'] = cmf_encrypt($res['data']['vmhost']['password']);
		$update['domain'] = $res['data']['vmhost']['hostname'];
		$flow_limit = $res['data']['vmhost']['flowLimit'] ?? 0;
		$extra_flow_limit = $res['data']['vmhost']['extraFlowLimit'] ?? 0;
		$update['bwlimit'] = $flow_limit > 0 ? round(($flow_limit + $extra_flow_limit)/1024/1024/1024,1) : 0;
		$update['bwusage'] = round($res['data']['vmhost']['usedFlow']/1024/1024/1024,1);

		$username = $res['data']['vmhost']['username'];
        $update['username'] = $username;
		$updated = Db::name('host')->where('id', $params['hostid'])->update($update);
		if($updated === false){
			return ['status'=>'error', 'msg'=>'本地流量信息同步失败'];
		}
		return ['status'=>'success', 'msg'=>$res['message']];
	}else{
		return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '同步失败')];
	}
}

// 云主机状态
function qimenidc_Status($params){
	$vserverid = qimenidc_GetServerid($params);
	if(empty($vserverid)){
		return 'QimenIDC VM ID错误';
	}
	// 设置请求参数
	$query = [
		'hostId'=>$vserverid,
	];
	$url = qimenidc_GetUrl($params, '/api/v1/pve/getVmInfo', $query);

	$res = qimenidc_Curl($params,$url, [], 30, 'GET');
	if(isset($res['code']) && $res['code'] == 20000){
		// 状态有0=运行中、1=已关机、2=挂起、3=恢复中、4=暂停、5=到期、6=创建中、7=开机中、8=关机中、9=停止中（强制关机中）、10=挂起中、11=暂停中、12重启中、13=重装系统中、14=修改密码中、15=超流断网
		$status = [
			0=>'运行中',
			1=>'已关机',
			2=>'挂起',
			3=>'恢复中',
			4=>'暂停',
			5=>'到期',
			6=>'创建中',
			7=>'开机中',
			8=>'关机中',
			9=>'停止中',
			10=>'挂起中',
			11=>'暂停中',
			12=>'重启中',
			13=>'重装系统中',
			14=>'修改密码中',
			15=>'超流断网',
		];
		// 定义状态代码
		$result['status'] = 'success';
		if(in_array($res['data']['vmhost']['status'], [0])){
			$result['data']['status'] = 'on';
			$result['data']['des'] = '开机';
		}else if(in_array($res['data']['vmhost']['status'], [1])){
			$result['data']['status'] = 'off';
			$result['data']['des'] = '关机';
		}else if(in_array($res['data']['vmhost']['status'], [2])){
			$result['data']['status'] = 'suspend';
			$result['data']['des'] = '挂起';
		}else if(in_array($res['data']['vmhost']['status'], [3, 6, 7, 8, 9, 10, 11, 12, 13, 14])){
			$result['data']['status'] = 'process';
			$result['data']['des'] = $status[$res['data']['vmhost']['status']];
		}else if($res['data']['vmhost']['status'] == 4){
			$result['data']['status'] = 'suspend';
			$result['data']['des'] = $res['data']['vmhost']['pauseInfo'];
		}else if($res['data']['vmhost']['status'] == 15){
			$result['data']['status'] = 'suspend';
			$result['data']['des'] = '流量超限';
		}else{
			$result['data']['status'] = 'unknown';
            $result['data']['des'] = '未知';
		}
		return $result;
	}else{
		return ['status'=>'error', 'msg'=>$res['message'] ?: '获取失败'];
	}
}

// 每天任务
function qimenidc_DailyCron(){
	// 每天按基础流量周期对账，过期包不再累计，付款接口失败可再次补齐。
	$time = time();
	$host_data = Db::name('host')
		->alias('a')
		->field('a.id,a.uid')
		->leftJoin('products b', 'a.productid=b.id')
		->leftJoin('servers c', 'a.serverid=c.id')
		->leftJoin('server_groups d', 'c.gid=d.id')
		->leftJoin('customfields e', 'a.productid=e.relid AND e.type="product" AND e.fieldname="vserverid"')
		->leftJoin('customfieldsvalues f', 'e.id=f.fieldid and a.id=f.relid')
		->whereIn('a.domainstatus', 'Active,Suspended')
		->where('a.nextduedate=0 OR a.nextduedate>'.$time)
		->where('a.serverid', '>', 0)
		->whereIn('b.api_type', ['','normal'])
		->where('d.system_type', 'normal')
		// 保留现有 QimenIDC 数据中心分组标识；本模块在部署中使用 nokvm。
		->where('d.type', 'nokvm')
		->where('f.value', '>', 0)
		->select()
		->toArray();
	$model = new \app\common\model\HostModel();
	foreach($host_data as $v){
		$params = $model->getProvisionParams($v['id']);
		$params['hostid'] = $v['id'];
		$params['uid'] = $v['uid'];
		qimenidc_FlowPacketPaid($params);
	}
}


// 后台按钮隐藏
function qimenidc_AdminButtonHide($params){
	if(!empty(qimenidc_GetServerid($params)) && $params['serverid']>0){
		return ['CreateAccount'];
	}else{
		return ['SuspendAccount','UnsuspendAccount','TerminateAccount','On','Off','Reboot','HardOff','HardReboot','Reinstall','CrackPassword','Vnc','Sync','resetFlow'];
	}
}

// 获取自定义字段value
function qimenidc_GetServerid($params){
	return (int)$params['customfields']['vserverid'];
	// return Db::name('customfields')
	// 		->alias('a')
	// 		->leftJoin('customfieldsvalues b', 'a.id=b.fieldid')
	// 		->where('a.type', 'product')
	// 		->where('a.relid', $params['productid'])
	// 		->where('a.fieldname', 'vserverid')
	// 		->where('b.relid', $params['hostid'])
	// 		->value('value');
}

// 创建签名
function qimenidc_CreateSign($token = ''){
	$data['timeStamp'] = time();
	$data['randomStr'] = randStr(6); 
	$data['token'] = $token;
	$res['time'] = $data['timeStamp'];
	$res['random'] = $data['randomStr'];
	sort($data, SORT_STRING);
	$str = implode($data);
	$signature = md5($str);
	$signature = strtoupper($signature);
	$res['signature'] = $signature;
	return $res;
}

// 获取请求地址
function qimenidc_GetUrl($params, $path = '/api/v1/pve/cerateVM', $query = []){
	$url = '';
	if($params['secure']){
		$url = 'https://';
	}else{
		$url = 'http://';
	}
	$url .= $params['server_ip'] ?: $params['server_host'];
	if(!empty($params['port'])){
		$url .= ':'.$params['port'];
	}
	$url .= $path;
	$q = '';
	foreach($query as $k=>$v){
		$q .= "&{$k}={$v}";
	}
	if(!empty($q)){
		$url = $url.'?'.ltrim($q, '&');
	}
	return $url;
}

// 统一请求方法
function qimenidc_Curl($params, $url, $data = [], $timeout = 30, $request = 'POST', $headers = []) {
    $curl = curl_init();
    $username = $params['server_username'];
    $password = $params['server_password'];

    if ($request == 'GET') {
        $queryString = http_build_query($data);
        $url .= ($queryString ? (strpos($url, '?') === false ? '?' : '&') . $queryString : '');
    }

    curl_setopt($curl, CURLOPT_URL, $url);
    curl_setopt($curl, CURLOPT_TIMEOUT, $timeout);
    curl_setopt($curl, CURLOPT_FOLLOWLOCATION, 1);
    curl_setopt($curl, CURLOPT_HEADER, 0);
    curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($curl, CURLOPT_SSL_VERIFYHOST, false);

    if (strtoupper($request) == 'GET') {
        curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
        curl_setopt($curl, CURLOPT_HTTPGET, 1);
    }

    if (strtoupper($request) == 'POST' || strtoupper($request) == 'PUT' || strtoupper($request) == 'DELETE') {
        curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
        curl_setopt($curl, CURLOPT_CUSTOMREQUEST, strtoupper($request));

        // 如果是数组，则转换为 JSON 格式
        if (is_array($data)) {
            $data = json_encode($data);
            if ($data === false) {
                curl_close($curl);
                return ['code' => 500, 'status' => 500, 'message' => '请求数据无法编码为 JSON'];
            }
        }

        curl_setopt($curl, CURLOPT_POSTFIELDS, $data);
        // 设置 Content-Type 为 application/json
		$headers[] = 'Content-Type: application/json';

    }

    // 添加认证头部
    if ($username && $password) {
        $authString = base64_encode("$username:$password");
        curl_setopt($curl, CURLOPT_HTTPHEADER, array_merge(['Authorization: Basic ' . $authString], $headers));
    }

    $res = curl_exec($curl);
    $error = curl_error($curl);

    $info = curl_getinfo($curl);
    curl_close($curl);
    if ($res === false || !empty($error)) {
        return ['code' => 500, 'status' => 500, 'message' => 'CURL ERROR: ' . $error];
    }
    $decoded = json_decode($res, true);
    if (($info['http_code'] ?? 0) < 200 || ($info['http_code'] ?? 0) >= 300) {
        return ['code' => 500, 'status' => $info['http_code'] ?? 500,
                'message' => is_array($decoded) ? ($decoded['message'] ?? 'HTTP请求失败') : 'HTTP请求失败'];
    }
    if (!is_array($decoded)) {
        return ['code' => 500, 'status' => 500, 'message' => '接口未返回有效 JSON 数据'];
    }
    return $decoded;
}



// function qimenidc_Curl($params,$url = '', $data = [], $timeout = 30, $request = 'POST', $header = []){
// 	$curl = curl_init();
//     if($request == 'GET'){
//         $s = '';
//         if(!empty($data)){
//             foreach($data as $k=>$v){
//                 $s .= $k.'='.urlencode($v).'&';
//             }
//         }
//         if($s){
//             $s = '?'.trim($s, '&');
//         }
//         curl_setopt($curl, CURLOPT_URL, $url.$s);
//     }else{
//         curl_setopt($curl, CURLOPT_URL, $url);
//     }
//     curl_setopt($curl, CURLOPT_TIMEOUT, $timeout);
//     curl_setopt($curl, CURLOPT_USERAGENT, 'WHMCS');
//     curl_setopt($curl, CURLOPT_FOLLOWLOCATION, 1);
//     curl_setopt($curl, CURLOPT_HEADER, 0);
//     //curl_setopt($curl, CURLOPT_COOKIESESSION, 1);
//     curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, false); 
//     curl_setopt($curl, CURLOPT_SSL_VERIFYHOST, false);
//     if(strtoupper($request) == 'GET'){
//         curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
//         curl_setopt($curl, CURLOPT_HTTPGET, 1);
//     }
//     if(strtoupper($request) == 'POST'){
//         curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
//         curl_setopt($curl, CURLOPT_POST, 1);
//         if(is_array($data)){
//             curl_setopt($curl, CURLOPT_POSTFIELDS, http_build_query($data));
//         }else{
//             curl_setopt($curl, CURLOPT_POSTFIELDS, $data);
//         }
//     }
//     if(strtoupper($request) == 'PUT' || strtoupper($request) == 'DELETE'){
//         curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
//         curl_setopt($curl, CURLOPT_CUSTOMREQUEST, strtoupper($request));
//         if(is_array($data)){
//             curl_setopt($curl, CURLOPT_POSTFIELDS, http_build_query($data));
//         }else{
//             curl_setopt($curl, CURLOPT_POSTFIELDS, $data);
//         }
//     }
//     if(!empty($header)){
//         curl_setopt($curl, CURLOPT_HTTPHEADER, $header);
//     }
//     $res = curl_exec($curl);
//     $error = curl_error($curl);
//     if(!empty($error)){
//     	return ['status'=>500, 'message'=>'CURL ERROR:'.$error];
//     }
//     $info = curl_getinfo($curl);
//     curl_close($curl);
//     return json_decode($res, true);
// }

// 新增方法
function qimenidc_downloadResource()
{
    $metaData = qimenidc_MetaData();

    $result = [
        'status' => 200,
        'msg'	 => '请求成功',
        'data'	 => [
            'module' => 'nokvm',
            'url' => request()->domain() . '/plugins/servers/nokvm/data/abc.zip' , // 下载路径
            'version' => $metaData['version'] ?? '1.0.0',
        ]
    ];
    return $result;
}
// 获取Nat信息
function qimenidc_getNatInfo($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
	// 设置请求参数
	$query = [
		'hostId'=>$vserverid,
	];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/nat/getInfo', $query);

    $res = qimenidc_Curl($params,$url, [], 15, 'GET');
    return $res;
}
// 获取Nat规则
function qimenidc_getNatVm($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
	// 设置请求参数
	$query = [
		'hostId'=>$vserverid,
		'page'=>1,
		'size'=>1000000,
	];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/nat/getVm', $query);

    $res = qimenidc_Curl($params,$url, [], 15, 'GET');
    return $res;
}
// 新增Nat规则
function qimenidc_addNatAcl($params){
    $post = input('post.');
    $destination_port = $post['int_port'];
    $source_port = $post['ext_port'];
    $protocol = $post['select-protocol'];
    if ($protocol == '1') {
        $protocol = 'tcp';
    } else if ($protocol == '2') {
        $protocol = 'udp';
    } else if ($protocol == '3') {
        $protocol = 'all';
    }
    if($source_port === '' || $source_port > 59000 || $source_port < 10000)
    {
        $source_port = random_int(10000, 59000);//外部端口范围不对则随机
    }
    
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
    
    if( count(qimenidc_getNatVm($params)["data"]) >= $params['configoptions']['natnum'] ){
        return '超出最大NAT创建数量';
    }
    
	// 设置请求参数
	$post_data = array(
        'source_port'=>$source_port,
		'destination_ip'=>$params["dedicatedip"],
		'destination_port'=>$destination_port,
		'protocol'=>$protocol,
		'vm'=>$vserverid
    );


    $url = qimenidc_GetUrl($params, '/api/v1/pve/nat/add');

    $res = qimenidc_Curl($params,$url,$post_data);

    if ($res['code'] == 20000) {
        return ['status'=>'success','msg'=>$res['message']];
    } else {
        return $res['message'];
    }
}
// 删除Nat规则
function qimenidc_delNatAcl($params){
    $post = input('post.');
    $source_port = $post['source_port'];
    $destination_port = $post['destination_port'];
    $protocol = $post['protocol'];
    
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return 'QimenIDC VM ID错误';
    }
	// 设置请求参数
	$post_data = array(
        'source_port'=>(int)$source_port,
		'destination_ip'=>$params["dedicatedip"],
		'destination_port'=>(int)$destination_port,
		'protocol'=>$protocol,
		'vm'=>(int)$vserverid
    );
    
    $url = qimenidc_GetUrl($params, '/api/v1/pve/nat/del');

    $res = qimenidc_Curl($params,$url,$post_data);
    if ($res['code'] == 20000) {
        return ['status'=>'success','msg'=>$res['message']];
    } else {
        return $res['message'];
    }
}

function qimenidc_apiSuccess($res){
    return is_array($res) && isset($res['code']) && $res['code'] == 20000;
}

function qimenidc_apiMessage($res, $default = ''){
    return is_array($res) ? ($res['message'] ?? $default) : $default;
}

function qimenidc_securityGroupDataList($res){
    if(!is_array($res)){
        return [];
    }
    if(isset($res['data']['records']) && is_array($res['data']['records'])){
        return $res['data']['records'];
    }
    if(isset($res['data']['data']['records']) && is_array($res['data']['data']['records'])){
        return $res['data']['data']['records'];
    }
    if(isset($res['data']['data']['data']) && is_array($res['data']['data']['data'])){
        return $res['data']['data']['data'];
    }
    if(isset($res['data']['data']) && is_array($res['data']['data'])){
        return $res['data']['data'];
    }
    if(isset($res['data']['list']) && is_array($res['data']['list'])){
        return $res['data']['list'];
    }
    if(isset($res['data']) && is_array($res['data'])){
        return $res['data'];
    }
    return [];
}

function qimenidc_securityGroupData($res){
    if(!is_array($res)){
        return [];
    }
    if(isset($res['data']['data']) && is_array($res['data']['data'])){
        return $res['data']['data'];
    }
    if(isset($res['data']['securityGroup']) && is_array($res['data']['securityGroup'])){
        return $res['data']['securityGroup'];
    }
    if(isset($res['data']) && is_array($res['data'])){
        return $res['data'];
    }
    return [];
}

function qimenidc_normalizeSecurityGroups($res){
    $groups = [];
    foreach(qimenidc_securityGroupDataList($res) as $group){
        if(!is_array($group)){
            continue;
        }
        $id = $group['id'] ?? $group['groupId'] ?? 0;
        if(empty($id)){
            continue;
        }
        $groups[] = [
            'id'=>(int)$id,
            'name'=>$group['name'] ?? $group['groupName'] ?? '-',
            'desc'=>$group['description'] ?? $group['desc'] ?? $group['remark'] ?? '',
            'erasable'=>!empty($group['isDefault']) || !empty($group['system']) || !empty($group['isTemplate']) ? 1 : 0,
            'used'=>(isset($group['applyStatus']) && (int)$group['applyStatus'] === 1) || !empty($group['applied']) || !empty($group['isApplied']) || !empty($group['used']) ? 1 : 0,
        ];
    }
    return $groups;
}

function qimenidc_fillSecurityGroupApplyStatus($params, $groups){
    if(empty($groups) || !is_array($groups)){
        return [];
    }
    foreach($groups as $key=>$group){
        $detail = qimenidc_getSecurityGroup($params, $group['id']);
        if(!qimenidc_apiSuccess($detail)){
            continue;
        }
        $data = qimenidc_securityGroupData($detail);
        if(isset($data['applyStatus'])){
            $groups[$key]['used'] = (int)$data['applyStatus'] === 1 ? 1 : 0;
        }
    }
    return $groups;
}

function qimenidc_normalizeSecurityRules($res){
    $rules = [];
    foreach(qimenidc_securityGroupDataList($res) as $rule){
        if(!is_array($rule)){
            continue;
        }
        $direction = $rule['direction'] ?? '';
        $action = $rule['action'] ?? '';
        $rules[] = [
            'id'=>$rule['id'] ?? 0,
            'virtual_security_groups_id'=>$rule['groupId'] ?? $rule['securityGroupId'] ?? 0,
            'orientation'=>$direction == 'egress' ? '出' : '入',
            'tactics'=>$action == 'drop' || $action == 'reject' ? '拒绝' : '允许',
            'type'=>$rule['protocol'] ?? 'all',
            'port_start'=>$rule['portStart'] ?? '-',
            'port_end'=>$rule['portEnd'] ?? '-',
            'ip_start'=>$rule['remoteCidr'] ?? ($rule['remoteIp'] ?? '0.0.0.0/0'),
            'ip_end'=>$rule['remoteGroupId'] ?? '',
            'level'=>$rule['priority'] ?? '',
            'desc'=>$rule['remark'] ?? $rule['description'] ?? '',
        ];
    }
    return $rules;
}

function qimenidc_securityRemoteCidr($ip_start, $ip_end = ''){
    $ip_start = trim((string)$ip_start);
    $ip_end = trim((string)$ip_end);
    if($ip_start === '' || $ip_start === '0.0.0.0'){
        return '0.0.0.0/0';
    }
    if(strpos($ip_start, '/') !== false){
        return $ip_start;
    }
    return $ip_start.'/32';
}

function qimenidc_getSecurityGroups($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>500, 'message'=>'QimenIDC VM ID error'];
    }

    $query = [
        'hostId'=>$vserverid,
        'page'=>1,
        'size'=>1000000,
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/page', $query);
    return qimenidc_Curl($params, $url, [], 30, 'GET');
}

function qimenidc_getSecurityGroup($params, $id){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>500, 'message'=>'QimenIDC VM ID error'];
    }

    $query = [
        'hostId'=>$vserverid,
        'id'=>(int)$id,
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/get', $query);
    return qimenidc_Curl($params, $url, [], 30, 'GET');
}

function qimenidc_CreateSecurityGroup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $name = trim($post['name'] ?? '');
    if($name === ''){
        return ['status'=>'error', 'msg'=>'安全组名称不能为空'];
    }

    $post_data = [
        'hostId'=>$vserverid,
        'name'=>$name,
        'description'=>trim($post['desc'] ?? $post['description'] ?? ''),
        'defaultIngressAction'=>'drop',
        'defaultEgressAction'=>'accept',
        'isTemplate'=>0,
        'isDefault'=>0,
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/add');
    $res = qimenidc_Curl($params, $url, $post_data, 30, 'POST');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '创建安全组成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '创建安全组失败')];
}

function qimenidc_DeleteSecurityGroup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $id = (int)($post['id'] ?? 0);
    if(empty($id)){
        return ['status'=>'error', 'msg'=>'安全组ID错误'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/delete');
    $res = qimenidc_Curl($params, $url, ['id'=>$id, 'hostId'=>$vserverid], 30, 'DELETE');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '删除安全组成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '删除安全组失败')];
}

function qimenidc_ApplySecurityGroup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $id = (int)($post['id'] ?? 0);
    if(empty($id)){
        return ['status'=>'error', 'msg'=>'安全组ID错误'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/apply');
    $res = qimenidc_Curl($params, $url, ['hostId'=>$vserverid, 'groupId'=>$id], 30, 'POST');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '应用安全组成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '应用安全组失败')];
}

function qimenidc_UnapplySecurityGroup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $id = (int)($post['id'] ?? 0);
    $post_data = ['hostId'=>$vserverid];
    if(!empty($id)){
        $post_data['groupId'] = $id;
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/unapply');
    $res = qimenidc_Curl($params, $url, $post_data, 30, 'POST');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '取消应用安全组成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '取消应用安全组失败')];
}

function qimenidc_ShowSecurityGroupAcl($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error', 'list'=>[]];
    }

    $id = (int)($post['id'] ?? 0);
    if(empty($id)){
        return ['status'=>'error', 'msg'=>'安全组ID错误', 'list'=>[]];
    }

    $query = [
        'hostId'=>$vserverid,
        'groupId'=>$id,
    ];
    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/rule/list', $query);
    $res = qimenidc_Curl($params, $url, [], 30, 'GET');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '获取安全组策略成功'), 'list'=>qimenidc_normalizeSecurityRules($res)];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '获取安全组策略失败'), 'list'=>[]];
}

function qimenidc_CreateSecurityGroupAcl($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $group_id = (int)($post['id'] ?? $post['groupId'] ?? 0);
    if(empty($group_id)){
        return ['status'=>'error', 'msg'=>'安全组ID错误'];
    }

    $protocol = trim($post['type'] ?? $post['protocol'] ?? 'all');
    $post_data = [
        'hostId'=>$vserverid,
        'groupId'=>$group_id,
        'direction'=>($post['orientation'] ?? '1') == '0' ? 'egress' : 'ingress',
        'protocol'=>$protocol,
        'remoteCidr'=>qimenidc_securityRemoteCidr($post['ip_start'] ?? '', $post['ip_end'] ?? ''),
        'action'=>($post['tactics'] ?? '0') == '1' ? 'drop' : 'accept',
        'priority'=>(int)($post['level'] ?? 100),
        'remark'=>trim($post['desc'] ?? $post['remark'] ?? ''),
    ];

    if(!in_array($protocol, ['icmp', 'all'])){
        $post_data['portStart'] = (int)($post['port_start'] ?? 0);
        $post_data['portEnd'] = (int)($post['port_end'] ?? $post_data['portStart']);
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/rule/add');
    $res = qimenidc_Curl($params, $url, $post_data, 30, 'POST');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '创建安全组策略成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '创建安全组策略失败')];
}

function qimenidc_DeleteSecurityGroupAcl($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $id = (int)($post['id'] ?? 0);
    if(empty($id)){
        return ['status'=>'error', 'msg'=>'安全组策略ID错误'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/securityGroup/rule/delete');
    $res = qimenidc_Curl($params, $url, ['hostId'=>$vserverid, 'id'=>$id], 30, 'DELETE');

    if(qimenidc_apiSuccess($res)){
        return ['status'=>'success', 'msg'=>qimenidc_apiMessage($res, '删除安全组策略成功')];
    }
    return ['status'=>'error', 'msg'=>qimenidc_apiMessage($res, '删除安全组策略失败')];
}

function qimenidc_getSnapshots($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>500, 'message'=>'QimenIDC VM ID error'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/getVmSnapShot/'.$vserverid);

    return qimenidc_Curl($params, $url, [], 30, 'GET');
}

function qimenidc_parseFeatureLimit($params, $key){
    if(!isset($params['configoptions'][$key]) || $params['configoptions'][$key] === ''){
        return -1;
    }
    return (int)$params['configoptions'][$key];
}
function qimenidc_Renew($params) {
    return "success";
}
function qimenidc_countSnapshots($snapshots){
    $count = 0;
    if(!is_array($snapshots)){
        return $count;
    }
    foreach($snapshots as $snapshot){
        if(($snapshot['name'] ?? '') === 'current'){
            continue;
        }
        $count++;
    }
    return $count;
}

function qimenidc_addSnapshot($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $snap_limit = qimenidc_parseFeatureLimit($params, 'snap_num');
    if($snap_limit < 0){
        return ['status'=>'error', 'msg'=>'当前产品不支持创建快照'];
    }
    if($snap_limit > 0){
        $snap_res = qimenidc_getSnapshots($params);
        $snapshots = [];
        if(isset($snap_res['code']) && $snap_res['code'] == 20000 && isset($snap_res['data']['data']) && is_array($snap_res['data']['data'])){
            $snapshots = $snap_res['data']['data'];
        }
        if(qimenidc_countSnapshots($snapshots) >= $snap_limit){
            return ['status'=>'error', 'msg'=>'快照数量已达上限'];
        }
    }

    $snapname = trim($post['snapname'] ?? '');
    if($snapname === ''){
        $snapname = 'snap_'.date('YmdHis');
    }

    $query = [
        'snapname'=>rawurlencode($snapname),
        'vmstate'=>isset($post['vmstate']) && ($post['vmstate'] === '1' || $post['vmstate'] === 'true') ? 'true' : 'false',
        'description'=>rawurlencode(trim($post['description'] ?? '')),
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/addVmSnapShot/'.$vserverid, $query);
    $res = qimenidc_Curl($params, $url, [], 30, 'POST');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '创建快照成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '创建快照失败'];
}

function qimenidc_deleteSnapshot($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $snapname = trim($post['snapname'] ?? '');
    if($snapname === '' || $snapname === 'current'){
        return ['status'=>'error', 'msg'=>'快照名称错误'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/deleteVmSnapShot/'.$vserverid.'/'.rawurlencode($snapname));
    $res = qimenidc_Curl($params, $url, [], 30, 'DELETE');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '删除快照成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '删除快照失败'];
}

function qimenidc_rollbackSnapshot($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $snapname = trim($post['snapname'] ?? '');
    if($snapname === '' || $snapname === 'current'){
        return ['status'=>'error', 'msg'=>'快照名称错误'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/rollbackVmSnapShot/'.$vserverid.'/'.rawurlencode($snapname));
    $res = qimenidc_Curl($params, $url, [], 30, 'POST');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '回滚快照成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '回滚快照失败'];
}

function qimenidc_getBackups($params){
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>500, 'message'=>'QimenIDC VM ID error'];
    }

    $url = qimenidc_GetUrl($params, '/api/v1/pve/getVmBackup/'.$vserverid);

    return qimenidc_Curl($params, $url, [], 30, 'GET');
}

function qimenidc_addBackup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $backup_limit = qimenidc_parseFeatureLimit($params, 'backup_num');
    if($backup_limit < 0){
        return ['status'=>'error', 'msg'=>'当前产品不支持创建备份'];
    }
    if($backup_limit > 0){
        $backup_res = qimenidc_getBackups($params);
        $backups = [];
        if(isset($backup_res['code']) && $backup_res['code'] == 20000 && isset($backup_res['data']['data']) && is_array($backup_res['data']['data'])){
            $backups = $backup_res['data']['data'];
        }
        if(count($backups) >= $backup_limit){
            return ['status'=>'error', 'msg'=>'备份数量已达上限'];
        }
    }

    $mode = trim($post['mode'] ?? 'snapshot');
    if(!in_array($mode, ['snapshot', 'suspend', 'stop'])){
        $mode = 'snapshot';
    }

    $compress = trim($post['compress'] ?? 'zstd');
    if(!in_array($compress, ['zstd', 'lzo', 'gzip', 'none'])){
        $compress = 'zstd';
    }

    $query = [
        'mode'=>rawurlencode($mode),
        'compress'=>rawurlencode($compress),
        'notes'=>rawurlencode(trim($post['notes'] ?? '{{guestname}}')),
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/addVmBackup/'.$vserverid, $query);
    $res = qimenidc_Curl($params, $url, [], 30, 'POST');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '创建备份成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '创建备份失败'];
}

function qimenidc_deleteBackup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $volid = trim($post['volid'] ?? '');
    if($volid === ''){
        return ['status'=>'error', 'msg'=>'备份ID错误'];
    }

    $query = [
        'volid'=>rawurlencode($volid),
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/deleteVmBackup/'.$vserverid, $query);
    $res = qimenidc_Curl($params, $url, [], 30, 'DELETE');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '删除备份成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '删除备份失败'];
}

function qimenidc_rollbackBackup($params){
    $post = input('post.');
    $vserverid = qimenidc_GetServerid($params);
    if(empty($vserverid)){
        return ['status'=>'error', 'msg'=>'QimenIDC VM ID error'];
    }

    $volid = trim($post['volid'] ?? '');
    if($volid === ''){
        return ['status'=>'error', 'msg'=>'备份ID错误'];
    }

    $query = [
        'volid'=>rawurlencode($volid),
        'force'=>isset($post['force']) && ($post['force'] === '1' || $post['force'] === 'true') ? 'true' : 'false',
        'start'=>isset($post['start']) && ($post['start'] === '1' || $post['start'] === 'true') ? 'true' : 'false',
    ];

    $url = qimenidc_GetUrl($params, '/api/v1/pve/rollbackVmBackup/'.$vserverid, $query);
    $res = qimenidc_Curl($params, $url, [], 30, 'POST');

    if(isset($res['code']) && $res['code'] == 20000){
        return ['status'=>'success', 'msg'=>$res['message'] ?: '还原备份成功'];
    }
    return ['status'=>'error', 'msg'=>$res['message'] ?: '还原备份失败'];
}

function qimenidc_AllowFunction(){
         return [
            'client'=>['addNatAcl','delNatAcl','CreateSecurityGroup','DeleteSecurityGroup','ApplySecurityGroup','UnapplySecurityGroup','ShowSecurityGroupAcl','CreateSecurityGroupAcl','DeleteSecurityGroupAcl','addSnapshot','deleteSnapshot','rollbackSnapshot','addBackup','deleteBackup','rollbackBackup'], 
            'admin'=>['resetFlow']
         ];
}
