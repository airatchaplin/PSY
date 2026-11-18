import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const endpoint = process.env.PSY_MINIO_ENDPOINT || 'http://127.0.0.1:9000'
const accessKey = process.env.PSY_MINIO_ACCESS_KEY || 'psy'
const secretKey = process.env.PSY_MINIO_SECRET_KEY || 'psysecret'
const bucket = process.env.PSY_MINIO_BUCKET || 'psy-prompts'
const region = 'us-east-1'
const promptsDir = path.resolve(__dirname, '../backend/src/main/resources/prompts')

function hmac(key, data) {
  return crypto.createHmac('sha256', key).update(data, 'utf8').digest()
}

function sha256Hex(data) {
  return crypto.createHash('sha256').update(data).digest('hex')
}

function amzDate(d = new Date()) {
  const iso = d.toISOString().replace(/[:-]|\.\d{3}/g, '')
  return { amz: iso, date: iso.slice(0, 8) }
}

async function signedRequest(method, urlPath, bodyBuf = Buffer.alloc(0), contentType = 'application/octet-stream') {
  const { amz, date } = amzDate()
  const host = new URL(endpoint).host
  const payloadHash = sha256Hex(bodyBuf)
  const canonicalHeaders =
    `content-type:${contentType}\n` +
    `host:${host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amz}\n`
  const signedHeaders = 'content-type;host;x-amz-content-sha256;x-amz-date'
  const canonicalRequest = [
    method,
    urlPath,
    '',
    canonicalHeaders,
    signedHeaders,
    payloadHash
  ].join('\n')
  const credentialScope = `${date}/${region}/s3/aws4_request`
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amz,
    credentialScope,
    sha256Hex(canonicalRequest)
  ].join('\n')
  const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, date), region), 's3'), 'aws4_request')
  const signature = crypto.createHmac('sha256', signingKey).update(stringToSign, 'utf8').digest('hex')
  const authorization =
    `AWS4-HMAC-SHA256 Credential=${accessKey}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`

  const res = await fetch(`${endpoint}${urlPath}`, {
    method,
    headers: {
      Authorization: authorization,
      'Content-Type': contentType,
      Host: host,
      'x-amz-content-sha256': payloadHash,
      'x-amz-date': amz
    },
    body: method === 'GET' || method === 'HEAD' ? undefined : bodyBuf
  })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(`${method} ${urlPath} -> ${res.status}: ${text}`)
  }
  return text
}

async function ensureBucket() {
  try {
    await signedRequest('PUT', `/${bucket}`)
    console.log(`bucket created: ${bucket}`)
  } catch (e) {
    if (String(e.message).includes(' -> 409')) {
      console.log(`bucket exists: ${bucket}`)
      return
    }
    // BucketAlreadyOwnedByYou sometimes 200; Head check
    try {
      await signedRequest('HEAD', `/${bucket}`)
      console.log(`bucket exists: ${bucket}`)
    } catch {
      throw e
    }
  }
}

async function putObject(key, content) {
  const body = Buffer.from(content, 'utf8')
  await signedRequest('PUT', `/${bucket}/${encodeURIComponent(key).replace(/%2F/g, '/')}`, body, 'text/plain; charset=utf-8')
  console.log(`uploaded: ${key} (${body.length} bytes)`)
}

async function listObjects() {
  // Minimal list via GET /bucket?list-type=2 for verification output.
  const { amz, date } = amzDate()
  const host = new URL(endpoint).host
  const payloadHash = sha256Hex('')
  const canonicalUri = `/${bucket}`
  const canonicalQuery = 'list-type=2'
  const canonicalHeaders =
    `host:${host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amz}\n`
  const signedHeaders = 'host;x-amz-content-sha256;x-amz-date'
  const canonicalRequest = [
    'GET',
    canonicalUri,
    canonicalQuery,
    canonicalHeaders,
    signedHeaders,
    payloadHash
  ].join('\n')
  const credentialScope = `${date}/${region}/s3/aws4_request`
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amz,
    credentialScope,
    sha256Hex(canonicalRequest)
  ].join('\n')
  const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, date), region), 's3'), 'aws4_request')
  const signature = crypto.createHmac('sha256', signingKey).update(stringToSign, 'utf8').digest('hex')
  const authorization =
    `AWS4-HMAC-SHA256 Credential=${accessKey}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`
  const res = await fetch(`${endpoint}${canonicalUri}?${canonicalQuery}`, {
    headers: {
      Authorization: authorization,
      Host: host,
      'x-amz-content-sha256': payloadHash,
      'x-amz-date': amz
    }
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`LIST -> ${res.status}: ${text}`)
  const keys = [...text.matchAll(/<Key>([^<]+)<\/Key>/g)].map(m => m[1])
  console.log(`objects (${keys.length}): ${keys.join(', ')}`)
}

async function main() {
  const files = fs.readdirSync(promptsDir).filter(f => f.endsWith('.txt'))
  if (!files.length) throw new Error(`No prompts in ${promptsDir}`)
  await ensureBucket()
  for (const file of files) {
    const content = fs.readFileSync(path.join(promptsDir, file), 'utf8')
    await putObject(file, content)
  }
  await listObjects()
  console.log(`done: ${files.length} prompts in s3://${bucket}`)
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
