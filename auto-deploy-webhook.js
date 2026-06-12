#!/usr/bin/env node
//==============================================================
// Auto-deploy Webhook Receiver
// Listens for GitHub push events and triggers redeployment
//==============================================================

const http = require('http');
const crypto = require('crypto');
const { exec } = require('child_process');
const fs = require('fs');

const PORT = process.env.WEBHOOK_PORT || 9000;
const SECRET = process.env.WEBHOOK_SECRET || '';  // Must match GitHub webhook secret
const DEPLOY_SCRIPT = '/opt/online-exam/auto-deploy.sh';
const LOG_FILE = '/opt/online-exam/deploy.log';

function log(msg) {
  const line = `[${new Date().toISOString()}] ${msg}\n`;
  console.log(line.trim());
  fs.appendFileSync(LOG_FILE, line);
}

function verifySignature(payload, signature) {
  if (!SECRET) return true;  // No secret configured, skip verification
  if (!signature) return false;
  const sig = `sha256=${crypto.createHmac('sha256', SECRET).update(payload).digest('hex')}`;
  return crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(signature));
}

function runDeploy(branch, commitMsg, author) {
  return new Promise((resolve, reject) => {
    log(`Deploy started: branch=${branch}, by=${author}, msg="${commitMsg}"`);
    exec(`bash ${DEPLOY_SCRIPT}`, { timeout: 300000 }, (err, stdout, stderr) => {
      if (err) {
        log(`Deploy FAILED: ${stderr || err.message}`);
        reject(err);
      } else {
        log('Deploy completed successfully.');
        resolve(stdout);
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  // Only accept POST to /webhook
  if (req.method !== 'POST' || req.url !== '/webhook') {
    res.writeHead(404);
    res.end('Not found');
    return;
  }

  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', async () => {
    // Verify signature
    const signature = req.headers['x-hub-signature-256'];
    if (!verifySignature(body, signature)) {
      log('Rejected: invalid signature');
      res.writeHead(401);
      res.end('Invalid signature');
      return;
    }

    // Parse event
    const event = req.headers['x-github-event'];
    log(`Received event: ${event}`);

    if (event === 'ping') {
      res.writeHead(200);
      res.end('OK');
      return;
    }

    if (event !== 'push') {
      res.writeHead(200);
      res.end('Ignored non-push event');
      return;
    }

    try {
      const data = JSON.parse(body);
      const branch = data.ref.replace('refs/heads/', '');
      const commitMsg = data.head_commit?.message || 'no message';
      const author = data.head_commit?.author?.name || 'unknown';

      // Only deploy for master/main branch
      if (branch !== 'master' && branch !== 'main') {
        log(`Skipped: branch "${branch}" is not master/main`);
        res.writeHead(200);
        res.end('Skipped non-master push');
        return;
      }

      // Run deployment
      res.writeHead(202);
      res.end('Deploy triggered');
      await runDeploy(branch, commitMsg, author);
    } catch (err) {
      log(`Error: ${err.message}`);
      if (!res.headersSent) {
        res.writeHead(500);
        res.end('Deploy error');
      }
    }
  });
});

server.listen(PORT, () => {
  log(`Webhook receiver listening on port ${PORT}`);
  log(`GitHub should POST to: http://YOUR_SERVER_IP:${PORT}/webhook`);
});
