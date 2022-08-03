const { AsyncClient } = require('..')
const RedisServer = require('redis-server')
const FreePort = require('find-free-port')

function OpenServerAndExecute(port, action) {
  return new Promise((resolve, reject) => {
    const server = new RedisServer(port)
    server.open(async (err) => {
      if (err) {
        reject(err)
      }
      await action()
      server.close()
      resolve()
    })
  })
}

it('set and get flow works', async () => {
  let port = await FreePort(3000).then(([free_port]) => free_port)
  await OpenServerAndExecute(port, async () => {
    let client = await AsyncClient.CreateConnection('redis://localhost:' + port)

    let date = new Date().toString()
    const key = 'key'
    await client.set(key, date)
    const result = await client.get(key)

    expect(result).toEqual(date)
  })
})

it('get for missing key returns null', async () => {
  let port = await FreePort(3000).then(([free_port]) => free_port)
  console.log(port)
  await OpenServerAndExecute(port, async () => {
    let client = await AsyncClient.CreateConnection('redis://localhost:' + port)

    const key = 'key2'
    const result = await client.get(key)

    expect(result).toEqual(null)
  })
})
