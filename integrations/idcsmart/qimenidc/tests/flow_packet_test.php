<?php
namespace think {
    class Db {
        public static $records = [];
        public static $hosts = [];
        public static $updates = [];
        public static $update_result = 1;
        public static function name($table){ return new Query($table); }
    }
    class Query {
        private $table;
        private $filters = [];
        public function __construct($table){ $this->table = $table; }
        public function alias($value){ return $this; }
        public function field($value){ return $this; }
        public function leftJoin($table, $condition){ return $this; }
        public function whereIn($field, $values){ return $this; }
        public function where(...$arguments){ $this->filters[] = $arguments; return $this; }
        public function select(){ return $this; }
        public function toArray(){
            foreach($this->filters as $filter){
                if($filter[0] === 'd.type'){
                    return array_values(array_filter(Db::$hosts, function($host) use ($filter){
                        return $host['module'] === $filter[1];
                    }));
                }
            }
            return Db::$hosts;
        }
        public function update($data){ Db::$updates[] = $data; return Db::$update_result; }
        public function sum($field){
            $total = 0;
            foreach(Db::$records as $record){
                $match = true;
                foreach($this->filters as $filter){
                    $operator = count($filter) === 2 ? '=' : $filter[1];
                    $expected = $filter[count($filter) - 1];
                    $actual = $record[$filter[0]];
                    if($operator === '=') $match = $match && $actual == $expected;
                    if($operator === '>=') $match = $match && $actual >= $expected;
                    if($operator === '<') $match = $match && $actual < $expected;
                }
                if($match) $total += $record[$field];
            }
            return $total;
        }
    }
}
namespace app\common\model {
    class HostModel {
        public static $params;
        public function getProvisionParams($host_id){ return self::$params; }
    }
}
namespace {
    require dirname(__DIR__) . '/qimenidc.php';
    function cmf_encrypt($value){ return 'encrypted:' . $value; }
    function check($condition, $message){
        if(!$condition) throw new \RuntimeException($message);
    }
    error_reporting(E_ALL & ~E_DEPRECATED);
    set_error_handler(function($severity, $message, $file, $line){
        if(!(error_reporting() & $severity)) return false;
        throw new \ErrorException($message, 0, $severity, $file, $line);
    });
    date_default_timezone_set('Asia/Shanghai');
    $socket = stream_socket_server('tcp://127.0.0.1:0', $error_number, $error_message);
    check($socket !== false, 'Could not allocate a loopback test port');
    $address = stream_socket_get_name($socket, false);
    fclose($socket);
    $state_path = tempnam(sys_get_temp_dir(), 'qimen-flow-state-');
    $log_path = tempnam(sys_get_temp_dir(), 'qimen-flow-log-');
    $environment = array_merge(getenv(), ['QIMEN_TEST_STATE'=>$state_path]);
    $process = proc_open([PHP_BINARY, '-n', '-S', $address, __DIR__ . '/http_fixture.php'],
        [0=>['pipe', 'r'], 1=>['file', $log_path, 'a'], 2=>['file', $log_path, 'a']],
        $pipes, __DIR__, $environment);
    check(is_resource($process), 'Could not start local HTTP fixture');
    fclose($pipes[0]);
    try {
        $ready = false;
        for($attempt = 0; $attempt < 50; $attempt++){
            $connection = @stream_socket_client('tcp://' . $address, $error_number, $error_message, 0.1);
            if($connection !== false){ fclose($connection); $ready = true; break; }
            usleep(20000);
        }
        check($ready, 'Local HTTP fixture did not start');
        $port = explode(':', $address)[1];
        $params = [
            'hostid'=>11, 'uid'=>22, 'customfields'=>['vserverid'=>33],
            'secure'=>false, 'server_ip'=>'127.0.0.1', 'server_host'=>'127.0.0.1', 'port'=>$port,
            'server_username'=>'test-manager', 'server_password'=>'test-password',
            'configoptions'=>['flow_limit'=>100, 'nat'=>0],
        ];
        $month_start = strtotime(date('Y-m-01 00:00:00'));
        $next_month = strtotime('+1 month', $month_start);
        $record = ['type'=>'flow_packet', 'hostid'=>11, 'uid'=>22, 'status'=>1, 'show_status'=>0];
        \think\Db::$records = [
            $record + ['pay_time'=>$month_start, 'capacity'=>25],
            $record + ['pay_time'=>$month_start + 1, 'capacity'=>100],
            $record + ['pay_time'=>$month_start - 1, 'capacity'=>900],
            $record + ['pay_time'=>$next_month, 'capacity'=>900],
            array_replace($record, ['status'=>0, 'pay_time'=>$month_start + 1, 'capacity'=>900]),
            array_replace($record, ['hostid'=>12, 'pay_time'=>$month_start + 1, 'capacity'=>900]),
            array_replace($record, ['uid'=>23, 'pay_time'=>$month_start + 1, 'capacity'=>900]),
        ];
        $reset = function($scenario = '', $flow_limit = 100, $create_time = null, $reset_flow_time = 1, $extra_flow_limit = 0) use ($state_path){
            $create_time = $create_time ?? (strtotime(date('Y-m-01 00:00:00')) * 1000);
            file_put_contents($state_path, json_encode(['scenario'=>$scenario, 'requests'=>[],
                'flowLimit'=>$flow_limit * 1024 * 1024 * 1024,
                'extraFlowLimit'=>$extra_flow_limit * 1024 * 1024 * 1024,
                'createTime'=>$create_time, 'resetFlowTime'=>$reset_flow_time]));
            \think\Db::$updates = [];
            \think\Db::$update_result = 1;
        };
        $state = function() use ($state_path){ return json_decode(file_get_contents($state_path), true); };

        $reset();
        $cycle = qimenidc_FlowCycleWindow([
            'createTime'=>$month_start * 1000, 'resetFlowTime'=>1,
        ], $month_start + 86400);
        check($cycle === [$month_start, $next_month], 'ResetFlowTime=1 should follow calendar-month boundaries');
        check(qimenidc_FlowCycleCapacity($params, [
            'createTime'=>$month_start * 1000, 'resetFlowTime'=>1,
        ], $month_start + 86400) === 125, 'Monthly cycle must include its start and exclude records after its end');
        $reset('', 100, null, 1, 37);
        $chart_params = $params;
        $chart_params['chart'] = ['type'=>'flow', 'start'=>(time() - 3600) * 1000];
        $chart = qimenidc_ChartData($chart_params);
        check($chart['status'] === 'success', 'Flow chart retrieval should synchronize the host first');
        check(count($state()['requests']) === 2
            && $state()['requests'][0]['path'] === '/api/v1/pve/getVmInfo'
            && $state()['requests'][1]['path'] === '/api/v1/pve/getVmInfo',
            'Flow chart retrieval should use two GET requests');
        check(count(\think\Db::$updates) === 1 && \think\Db::$updates[0]['bwlimit'] == 137,
            'Real-time flow retrieval must use API extraFlowLimit instead of local package records');
        check(\think\Db::$updates[0]['bwusage'] == 10, 'Real-time flow retrieval must synchronize usedFlow');
        check($state()['extraFlowLimit'] === 37 * 1024 * 1024 * 1024,
            'Real-time synchronization must not PUT the API flow package value');
        $reset('', 100, null, 1, 0);
        $chart = qimenidc_ChartData($chart_params);
        check($chart['status'] === 'success' && \think\Db::$updates[0]['bwlimit'] == 100,
            'Real-time synchronization must clear local package allowance when API extraFlowLimit is zero');
        $reset('missing-extra');
        $chart = qimenidc_ChartData($chart_params);
        check($chart['status'] === 'error' && count(\think\Db::$updates) === 0,
            'Missing API extraFlowLimit must fail without overwriting local data');
        $reset('', 100, null, 1, 37);
        $panel = qimenidc_ClientArea($params);
        check(is_array($panel) && count(\think\Db::$updates) === 1
            && \think\Db::$updates[0]['bwlimit'] == 137
            && \think\Db::$updates[0]['bwusage'] == 10
            && count($state()['requests']) === 1
            && $state()['requests'][0]['method'] === 'GET'
            && $state()['requests'][0]['path'] === '/api/v1/pve/getVmInfo',
            'Opening the client product page must synchronize the latest flow before rendering');
        $reset('', 100, null, 1, 37);
        $status = qimenidc_Status($params);
        check($status['status'] === 'success' && $status['data']['status'] === 'on'
            && count(\think\Db::$updates) === 1
            && \think\Db::$updates[0]['bwlimit'] == 137
            && \think\Db::$updates[0]['bwusage'] == 10,
            'Product status loading must synchronize flow before returning the status');
        $reset();
        check(qimenidc_FlowPacketPaid($params) === true, 'Paid flow package should update and sync');
        check(qimenidc_FlowPacketPaid($params) === true, 'Repeated payment callback should succeed');
        check($state()['extraFlowLimit'] === 125 * 1024 * 1024 * 1024, 'Repeated callback must not add twice');
        check(\think\Db::$updates[1]['bwlimit'] == 225, 'Local quota should display base plus packages');
        check($state()['requests'][1]['method'] === 'PUT', 'Purchase must use PUT');
        check($state()['requests'][1]['body'] === ['hostid'=>33, 'extraFlowLimit'=>125], 'Purchase must send absolute total GiB');
        check($state()['requests'][1]['content_type'] === 'application/json', 'Purchase must send the backend JSON contract');
        check($state()['requests'][1]['authorization'] === 'Basic ' . base64_encode('test-manager:test-password'), 'Purchase must authenticate with AppId and AppKey');

        $reset('api-failure');
        check(qimenidc_FlowPacketPaid($params) === false, 'Failed API update must not return success');
        check(count($state()['requests']) === 2 && count(\think\Db::$updates) === 0, 'Failed update must not run Sync');
        $reset('sync-failure');
        check(qimenidc_FlowPacketPaid($params) === false, 'Failed post-update API sync must not return success');
        check(count($state()['requests']) === 3 && count(\think\Db::$updates) === 0, 'Failed post-update sync must not update local quota');
        $reset('sync-incomplete');
        check(qimenidc_FlowPacketPaid($params) === false, 'Incomplete API sync response must not return success');
        $reset('metadata-failure');
        check(qimenidc_FlowPacketPaid($params) === false, 'Failed cycle metadata lookup must stop before quota update');
        check(count($state()['requests']) === 1, 'Failed cycle metadata lookup must not send a quota update');
        $reset('metadata-incomplete');
        check(qimenidc_FlowPacketPaid($params) === false, 'Missing cycle metadata must fail closed');
        check(count($state()['requests']) === 1, 'Missing cycle metadata must not send a quota update');
        $reset();
        $invalid_auth = $params;
        $invalid_auth['server_password'] = 'incorrect-test-password';
        check(qimenidc_FlowPacketPaid($invalid_auth) === false, 'Failed Basic auth must not return success');
        $reset();
        \think\Db::$update_result = false;
        check(qimenidc_FlowPacketPaid($params) === false, 'Failed local database sync must not return success');
        foreach(['http-failure', 'invalid-json'] as $scenario){
            $reset($scenario);
            check(qimenidc_FlowPacketPaid($params) === false, $scenario . ' must not return success');
        }

        $reset('', 0);
        check(qimenidc_FlowPacketPaid($params) === true, 'Unlimited quota should still sync');
        check(\think\Db::$updates[0]['bwlimit'] === 0, 'Base zero must remain unlimited despite purchased packages');
        $reset();
        $upgrade = $params;
        $upgrade['configoptions_upgrade'] = ['flow_limit'=>true];
        check(qimenidc_ChangePackage($upgrade)['status'] === 'success', 'Package upgrade must use numeric timeout and PUT');
        $reset('sync-failure');
        $upgrade_result = qimenidc_ChangePackage($upgrade);
        check($upgrade_result['status'] === 'error', 'Package upgrade must fail when post-update sync fails');

        $reset();
        \think\Db::$hosts = [['id'=>11, 'uid'=>22, 'module'=>'nokvm'], ['id'=>12, 'uid'=>23, 'module'=>'qimenidc']];
        \app\common\model\HostModel::$params = $params;
        qimenidc_DailyCron();
        check(count($state()['requests']) === 3, 'Daily reconciliation must send metadata, update and sync only for qimenidc hosts');
        check($state()['requests'][1]['body']['extraFlowLimit'] === 125, 'Daily reconciliation must repair missing quota');
        \think\Db::$records = [];
        qimenidc_DailyCron();
        check($state()['extraFlowLimit'] === 0, 'New month without purchases must revoke last month package allowance');

        $opening_day = strtotime('2026-10-14 00:00:00');
        $opening_create = strtotime('2026-09-14 12:00:00') * 1000;
        check(qimenidc_FlowCycleWindow([
            'createTime'=>$opening_create, 'resetFlowTime'=>0,
        ], $opening_day + 3600) === [$opening_day, strtotime('2026-11-14 00:00:00')], 'Opening-day cycle must include reset day and end exclusively');
        check(qimenidc_FlowCycleWindow([
            'createTime'=>$opening_create, 'resetFlowTime'=>0,
        ], $opening_day - 1) === [strtotime('2026-09-14 12:00:00'), $opening_day], 'First opening-day cycle must begin at creation and end at its first reset');
        \think\Db::$records = [
            $record + ['pay_time'=>$opening_day - 1, 'capacity'=>999],
            $record + ['pay_time'=>$opening_day, 'capacity'=>60],
            $record + ['pay_time'=>strtotime('2026-11-13 23:59:59'), 'capacity'=>40],
            $record + ['pay_time'=>strtotime('2026-11-14 00:00:00'), 'capacity'=>999],
        ];
        check(qimenidc_FlowCycleCapacity($params, [
            'createTime'=>$opening_create, 'resetFlowTime'=>0,
        ], $opening_day + 3600) === 100, 'Opening-day capacity must include the reset instant and exclude the next reset');
        $reset('', 100, $opening_create, 0);
        $configured_monthly = $params;
        $configured_monthly['configoptions']['resetFlowTime'] = 1;
        check(qimenidc_FlowPacketPaid($configured_monthly, $opening_day + 3600) === true, 'Purchase must follow authoritative backend resetFlowTime=0 rather than local config');
        check($state()['requests'][1]['body']['extraFlowLimit'] === 100, 'Opening-day purchase must send only unexpired cycle packages');
        check(\think\Db::$updates[0]['bwlimit'] == 200, 'Opening-day purchase must sync base plus active cycle package quota');
        \think\Db::$records = [
            $record + ['pay_time'=>$opening_day - 1, 'capacity'=>999],
        ];
        check(qimenidc_FlowPacketPaid($configured_monthly, $opening_day + 3600) === true, 'Opening-day reconciliation without new packages should still succeed');
        check($state()['extraFlowLimit'] === 0, 'Expired packages must never be re-credited after an opening-day reset');
        check(qimenidc_FlowCycleWindow([
            'createTime'=>strtotime('2026-01-31 12:00:00') * 1000, 'resetFlowTime'=>0,
        ], strtotime('2026-02-28 12:00:00')) === [strtotime('2026-02-28 00:00:00'), strtotime('2026-03-31 00:00:00')], 'January 31 opening day must clamp February then return to March 31');
        check(qimenidc_FlowCycleWindow([
            'createTime'=>strtotime('2026-01-31 12:00:00') * 1000, 'resetFlowTime'=>0,
        ], strtotime('2026-02-27 23:59:59')) === [strtotime('2026-01-31 12:00:00'), strtotime('2026-02-28 00:00:00')], 'First January 31 cycle must close on February 28');
        check(qimenidc_FlowCycleWindow([
            'createTime'=>strtotime('2028-01-31 12:00:00') * 1000, 'resetFlowTime'=>0,
        ], strtotime('2028-02-29 12:00:00')) === [strtotime('2028-02-29 00:00:00'), strtotime('2028-03-31 00:00:00')], 'Leap-year January 31 opening day must use February 29');
        check(qimenidc_FlowCycleCapacity($params, [], $opening_day) === false, 'Missing backend cycle metadata must fail closed');
        foreach([
            ['createTime'=>$opening_create, 'resetFlowTime'=>2],
            ['createTime'=>'bad-time', 'resetFlowTime'=>0],
            ['createTime'=>$opening_create, 'resetFlowTime'=>null],
        ] as $invalid_metadata){
            check(qimenidc_FlowCycleWindow($invalid_metadata, $opening_day) === false, 'Invalid backend cycle metadata must fail closed');
        }
        echo "PASS: cycle-bound payment, opening-day/month-end boundaries, retries, API/HTTP/JSON/sync failures, unlimited quota, upgrades and reconciliation\n";
    } finally {
        proc_terminate($process);
        proc_close($process);
        unlink($state_path);
        unlink($log_path);
    }
}
