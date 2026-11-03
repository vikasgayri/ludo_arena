# MongoDB collections

Database name comes from `MONGODB_URI` (default `ludo_arena`). Indexes are declared on the document classes and created at startup (`spring.data.mongodb.auto-index-creation=true`).

| Collection | Purpose | Key fields | Indexes |
|---|---|---|---|
| `users` | Accounts | `name,email,passwordHash,role,status,avatar,stats,cosmetics,preferences,activeRoomId` | unique `email`; `{status,createdAt}` |
| `wallets` | One per user | `userId,coinBalance,currencyType(NON_REDEEMABLE),appliedRefs[],lastDailyBonusAt` | unique `userId` |
| `walletTransactions` | Append-only ledger | `userId,type,amount,previousBalance,newBalance,referenceId,status,createdAt` | unique `referenceId`; `{userId,createdAt}`; `{type,status,createdAt}` |
| `rooms` | Lobby rooms | `code,name,hostId,maxPlayers,status,isPrivate,players[],matchId` | unique `code`; `{status,isPrivate,createdAt}`; `players.userId` |
| `matches` | Match + authoritative game state | `roomId,mode,status,playerIds[],game{…},winnerId,finalized` | `{playerIds,createdAt}`; `{status,game.turnDeadlineEpochMs}`; `roomId` |
| `matchEvents` | Per-action event log | `matchId,seq,eventId,type,userId,data,at` | unique `{matchId,seq}`; unique `eventId` |
| `leaderboards` | Ranking projection | `userId,name,avatar,xp,wins,totalMatches` | unique `userId`; `{xp:-1,wins:-1}` |
| `storeItems` | Cosmetic catalogue | `sku,name,category,priceCoins,rarity,assetKey,active` | unique `sku`; `category` |
| `userInventory` | Owned items | `userId,itemId,sku,pricePaid,transactionRef,purchasedAt` | unique `{userId,itemId}`; `{userId,purchasedAt}` |
| `notifications` | In-app notices | `userId,type,title,message,read,createdAt` | `{userId,read,createdAt}` |
| `adminLogs` | Immutable admin audit | `adminId,adminEmail,action,targetType,targetId,details,createdAt` | `createdAt` desc |
| `reports` | Player reports | `reporterId,type,targetUserId,matchId,description,status,adminNote` | `{status,createdAt}` |
| `refreshTokens` | Hashed refresh tokens | `userId,tokenHash,familyId,revoked,expiresAt` | unique `tokenHash`; TTL `expiresAt` |
| `passwordResetTokens` | Hashed reset tokens | `userId,tokenHash,used,expiresAt` | unique `tokenHash`; TTL `expiresAt` |
| `platformSettings` | Single doc `global` | rewards, bonuses, rule toggles, timeouts | n/a |
| `paymentOrders` | Payment orders | `userId,packageId,providerOrderId,status,providerPaymentId` | unique `providerOrderId`; sparse unique `providerPaymentId` |

Notes: tokens and reset codes are stored only as SHA-256 hashes. `wallets.appliedRefs` holds the last 50 applied reference ids (exactly-once balance updates). `matches.finalized` is claimed atomically so rewards, stats and leaderboard updates run once per match. Seeded on first start: 12 default store items, the `global` settings document, and optionally a `SUPER_ADMIN` from `ADMIN_BOOTSTRAP_*`.
