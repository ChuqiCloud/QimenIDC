<?php
// 仅供本地回归测试，不连接真实 QimenIDC 服务。
$state_path = getenv('QIMEN_TEST_STATE');
$state = json_decode(file_get_contents($state_path), true);
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$body = json_decode(file_get_contents('php://input'), true);
$state['requests'][] = ['method'=>$_SERVER['REQUEST_METHOD'], 'path'=>$path, 'body'=>$body,
    'authorization'=>$_SERVER['HTTP_AUTHORIZATION'] ?? '', 'content_type'=>$_SERVER['CONTENT_TYPE'] ?? ''];
$scenario = $state['scenario'] ?? '';
header('Content-Type: application/json');
if(($_SERVER['HTTP_AUTHORIZATION'] ?? '') !== 'Basic ' . base64_encode('test-manager:test-password')){
    http_response_code(401);
    file_put_contents($state_path, json_encode($state));
    echo json_encode(['code'=>501002, 'message'=>'API鉴权失败']);
    return;
}
if($scenario === 'http-failure'){
    http_response_code(503);
    file_put_contents($state_path, json_encode($state));
    echo json_encode(['code'=>20000, 'message'=>'HTTP failure must not count as API success']);
    return;
}
if($scenario === 'invalid-json'){
    file_put_contents($state_path, json_encode($state));
    echo '<html>proxy error</html>';
    return;
}
if($path === '/api/v1/pve/updateVm'){
    if($scenario === 'api-failure'){
        $response = ['code'=>50000, 'message'=>'额度更新失败'];
    } elseif($_SERVER['REQUEST_METHOD'] !== 'PUT' || !is_array($body)){
        $response = ['code'=>50000, 'message'=>'Expected JSON PUT request'];
    } else {
        if(isset($body['extraFlowLimit'])){
            $state['extraFlowLimit'] = $body['extraFlowLimit'] * 1024 * 1024 * 1024;
        }
        $response = ['code'=>20000, 'message'=>'updated'];
    }
} elseif($path === '/api/v1/pve/getVmInfo'){
    $info_request_count = count(array_filter($state['requests'], function($request){
        return $request['path'] === '/api/v1/pve/getVmInfo';
    }));
    $update_request_count = count(array_filter($state['requests'], function($request){
        return $request['path'] === '/api/v1/pve/updateVm';
    }));
    if($scenario === 'metadata-failure'
        || ($scenario === 'sync-failure' && ($info_request_count > 1 || $update_request_count > 0))){
        $response = ['code'=>50000, 'message'=>'读取失败'];
    } else {
        $response = ['code'=>20000, 'message'=>'synced', 'data'=>['vmhost'=>[
            'ipData'=>[['ip'=>'192.0.2.1']], 'password'=>'test', 'hostname'=>'vm-test',
            'username'=>'root', 'flowLimit'=>$state['flowLimit'],
            'extraFlowLimit'=>$state['extraFlowLimit'], 'usedFlow'=>10 * 1024 * 1024 * 1024,
            'createTime'=>$state['createTime'], 'resetFlowTime'=>$state['resetFlowTime'],
        ]]];
        if($scenario === 'metadata-incomplete' && $info_request_count === 1){
            $response['data']['vmhost']['createTime'] = null;
            $response['data']['vmhost']['resetFlowTime'] = null;
        }
        if($scenario === 'sync-incomplete' && $info_request_count > 1){
            $response = ['code'=>20000, 'message'=>'partial response', 'data'=>['vmhost'=>[]]];
        }
    }
} else {
    http_response_code(404);
    $response = ['code'=>50000, 'message'=>'Unknown test endpoint'];
}
file_put_contents($state_path, json_encode($state));
echo json_encode($response);
