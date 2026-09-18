#!/usr/bin/env python3
"""Local TaCZ assembly inspector. It never starts Minecraft or edits weapon content."""
from pathlib import Path
import argparse,hashlib,http.server,json,mimetypes,os,select,subprocess,threading,time
from urllib.parse import unquote,urlsplit

HERE=Path(__file__).resolve().parent
PROJECT=HERE.parents[3]
RUNTIME=PROJECT/'build/tacz-workbench/runtime.json'

def payload(value): return json.dumps(value,ensure_ascii=False,separators=(',',':')).encode()

class Worker:
    def __init__(self):
        runtime=json.loads(RUNTIME.read_text())
        self.process=subprocess.Popen([runtime['java'],'-cp',os.pathsep.join(runtime['classpath']),'dev.tacticaltacz.workbench.TaczWorkbenchWorker'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
        self.lock=threading.Lock()
    def call(self,value):
        with self.lock:
            if self.process.poll() is not None: raise RuntimeError(self.process.stderr.read() or 'TaCZ workbench worker stopped')
            self.process.stdin.write(json.dumps(value,separators=(',',':'))+'\n');self.process.stdin.flush()
            deadline=time.monotonic()+60;result=None
            # NeoForge/logging dependencies may print a one-line terminal warning
            # before the worker protocol starts. Only JSON objects belong to the protocol.
            while result is None:
                remaining=deadline-time.monotonic()
                if remaining<=0 or not select.select([self.process.stdout],[],[],remaining)[0]:raise TimeoutError('TaCZ workbench worker timed out')
                line=self.process.stdout.readline().strip()
                if line.startswith('{'):result=json.loads(line)
            if 'error' in result: raise ValueError(result['error'])
            return result
    def close(self):
        if self.process.poll() is None:self.process.terminate()

class App:
    def __init__(self):
        self.worker=None;self.state='building';self.error='';self.generation='';self.lock=threading.RLock();self.build()
    def build(self):
        try:
            subprocess.run(['bash',str(PROJECT/'gradlew'),'prepareTaczWorkbench','--offline'],cwd=PROJECT,check=True,timeout=300)
            worker=Worker();catalog=worker.call({'op':'catalog'})
            with self.lock:
                if self.worker:self.worker.close()
                self.worker=worker;self.catalog=catalog;self.generation=hashlib.sha256((RUNTIME.read_text()+str(time.time_ns())).encode()).hexdigest()[:12];self.state='ready';self.error=''
        except Exception as exc:
            with self.lock:self.state='failed';self.error=str(exc)
    def status(self):return {'state':self.state,'error':self.error,'generation':self.generation,'project':str(PROJECT),'message':'外置只读检查；不修改枪械、配件或游戏存档'}
    def rebuild(self):
        with self.lock:
            if self.state=='building':return
            self.state='building';self.error=''
        threading.Thread(target=self.build,daemon=True).start()

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def send(self,code,data,mime='application/json'):
        raw=payload(data) if mime=='application/json' else data
        self.send_response(code);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(raw)
    def local(self):
        host=self.headers.get('Host','');return host in (f'127.0.0.1:{self.server.server_port}',f'localhost:{self.server.server_port}')
    def do_GET(self):
        if not self.local():return self.send(403,{'error':'Local host required'})
        path=unquote(urlsplit(self.path).path)
        try:
            if path=='/api/status':return self.send(200,self.server.app.status())
            if path=='/api/catalog':
                if self.server.app.state!='ready':raise ValueError('Worker is not ready')
                return self.send(200,self.server.app.catalog)
            if path=='/':path='/index.html'
            if path in ('/index.html','/app.js','/style.css'):
                target=HERE/path[1:];return self.send(200,target.read_bytes(),mimetypes.guess_type(target.name)[0] or 'text/plain')
            return self.send(404,{'error':'Not found'})
        except Exception as exc:return self.send(409,{'error':str(exc)})
    def do_POST(self):
        if not self.local():return self.send(403,{'error':'Local host required'})
        try:
            length=int(self.headers.get('Content-Length','0'))
            if length<2 or length>32*1024*1024 or self.headers.get('Content-Type')!='application/json':raise ValueError('JSON request required')
            data=json.loads(self.rfile.read(length));path=urlsplit(self.path).path
            if path=='/api/assembly':return self.send(200,self.server.app.worker.call({'op':'assembly',**data}))
            if path=='/api/rebuild':self.server.app.rebuild();return self.send(202,{'message':'Rebuild requested'})
            return self.send(404,{'error':'Not found'})
        except Exception as exc:return self.send(409,{'error':str(exc)})

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8770);args=parser.parse_args()
    server=http.server.ThreadingHTTPServer(('127.0.0.1',args.port),Handler);server.app=App()
    print(f'TaCZ 枪械检查工作台：http://127.0.0.1:{args.port}/',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:
        if server.app.worker:server.app.worker.close()
        server.server_close()
if __name__=='__main__':main()
