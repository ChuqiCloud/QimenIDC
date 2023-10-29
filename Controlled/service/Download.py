import subprocess

'''
下载文件到指定目录
Download the file to the specified directory
'''
def run_wget(url, path):  
    #process = subprocess.Popen(['wget', '-P', path, url])  
    #支持断点续传
    process = subprocess.Popen(['wget', '-c', '-P', path, url])
    return process