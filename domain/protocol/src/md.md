# Multi-Device Protocol

TODO: Introduction, D2M, D2D, yadda yadda.

## Terminology

- `CK`: Client Key (permanent secret key associated to the Threema ID)
- `DGK`: Device Group Key
- `DGPK`: Device Group Path Key
- `ESK`: Ephemeral Server Key
- `DGRK`: Device Group Reflect Key
- `DGDIK`: Device Group Device Info Key
- `DGSDDK`: Device Group Shared Device Data Key
- `DGTSK`: Device Group Transaction Scope Key
- `Device ID`: Refers to the _Mediator Device ID_

## Key Derivation

All multi-device keys are derived from the Device Group Key `DGK`. The `DGK`
consists of 32 randomly generated bytes that will be distributed to all devices.
It persists as long as the device group exists.

    DGPK = BLAKE2b(key=DGK, salt='p', personal='3ma-mdev')
    DGRK = BLAKE2b(key=DGK, salt='r', personal='3ma-mdev')
    DGDIK = BLAKE2b(key=DGK, salt='di', personal='3ma-mdev')
    DGSDDK = BLAKE2b(key=DGK, salt='sdd', personal='3ma-mdev')
    DGTSK = BLAKE2b(key=DGK, salt='ts', personal='3ma-mdev')

## Mitigating Replay

To prevent replay attacks, the devices must permanently store used nonces for
incoming and outgoing D2D messages. D2D messages reusing previously used nonces
must not be processed and discarded. One nonce store for all D2D messages is
sufficient, even if different keys are being used.

Note that it is still possible for the Mediator server to replay old messages to
a newly added device.

### Exceptions

Messages encrypted by the following keys may be persistent:

- `DGDIK`
- `DGSDDK`

Since a persistent message is indistinguishable from a replay attack, a hash of
the most recent message of these keys must be stored, mapped to its associated
nonce.

When a message with one of these keys is being received:

1. Let `lm` be the last message received using the same key. Let `cm` be the
   current message that just has been received.
2. If the nonce of `cm` is equal to the nonce of `lm`:
   1. Compare the hash of `cm` against the hash of `lm`.
   2. If the hashes are not equal, log this encounter, discard `cm` and abort
      these steps.
3. Store the nonce of `cm` as _used_.
4. Store the hash and the nonce of `cm` as the new _last message_ (`lm`) for the
   provided key.

## Mitigating Payload Confusion

To prevent attacks where an encrypted payload can be interchanged with another
intended for a different scope, a different key for each scope must be used.

One example of payload confusion would be if the same key would be used to
encrypt `d2d.Envelope` for use in D2M `reflect`/`reflected` and to encrypt
`d2d.SharedDeviceData`. The mediator server could then interchange these two
messages and if the decrypted content is parsable, unintended handling logic
could be triggered on the devices. By using different keys for different scopes,
they are cryptographically tied to their scope and cannot be confused with
another.

## Transactions

The multi-device protocol makes heavy use of _transactions_ to be able to
execute an atomic operation shared across the device group.

Transaction steps always include a _scope_ (mapping to `d2d.TransactionScope`)
and a _precondition_ function.

A _precondition_ function determines whether following steps should be executed.
To be precise, if the end of the _precondition_ steps are reached, the
_precondition_ check was successful and subsequent steps are to be continued. If
the _precondition_ steps abort a process, they must explicitly define which part
of the surrounding steps are to be aborted.

It is vital to understand how to implement _precondition_ steps correctly. A
_precondition_ is executed in the following three cases:

1. Before sending a `d2m.BeginTransaction`. If the _precondition_ aborts, the
   transaction must not be initiated.
2. After receiving a `d2m.TransactionRejected`. If the _precondition_ aborts,
   the transaction must not be initiated again.
3. After receiving a `d2m.BeginTransactionAck`. If the _precondition_ aborts,
   the transaction must be ended gracefully by committing it via a
   `d2m.CommitTransaction`.

Note: When a _precondition_ aborts, the following steps (that would be executed
inside the transaction) are aborted, too. In most cases, this aborts a whole
chain of steps.

## Protocol Tasks

### Task

A protocol task is an arbitrary synchronous or asynchronous process that is run
until completion.

A task has one of the following types:

- _Persistent_: The task must be stored persistently (i.e. on disk), so it is
  rescheduled when the app is being terminated and restarted. The task exists
  until it completes.
- _Volatile_: The task must not be stored persistently (i.e. only exist in
  memory) but otherwise exists until it completes. When the app is being
  terminated and restarted, the task will be lost.
- _Drop On Disconnect_: The task must not be stored persistently (i.e. only
  exist in memory) and must be discarded when the current primary connection
  (i.e. a connection to the Chat Server / Mediator Server) becomes closed.

### Task Manager

Protocol tasks are managed sequentially and in order by the use of a single
protocol task manager with an internal queue. The protocol task manager has the
same lifetime as the application instance.

Any task that is scheduled in the protocol task manager will not be run until a
primary connection attempt (i.e. a connection to the Chat Server / Mediator
Server) is being made, meaning that tasks cannot advance while offline. Whenever
a task is being aborted exceptionally, the exception must bubble to abort the
current connection.

Whenever the primary connection closes, the task manager must cancel the
currently executed task and transfer the current and any other remaining tasks
of the queue into a new task queue. When doing so, all tasks with type _Drop On
Disconnect_ must be discarded.
