---
title: "Mutating Barter Shops"
order: 11
published: true
draft: false
---

# Mutating Barter Shops

A mutating barter shop generates its trade list randomly every time the server starts. Instead of static trades that are always the same, the shop picks random combinations from configured input and output item pools. This creates variety and gives players a reason to check merchants regularly.

## How It Works

A mutating shop asset defines one or more pool configurations. Each pool configuration has an input pool (what the player gives), an output pool (what the player receives), and a slot count (how many random trades to generate from this pool). On server start, the framework rolls the pools and injects the resulting trades into the associated `BarterShop` asset, replacing or merging with any static trades that asset may already have.

## Step 1 - Create the Base Barter Shop

First create a standard (possibly empty) `BarterShop` asset that the mutating shop will inject trades into. Create a file at `Server/BarterShops/MyMod_Shop_RandomMerchant.json`:

```json
{
  "Trades": []
}
```

This can be an empty list. The mutating shop will fill it on server start.

## Step 2 - Create the Mutating Shop Asset

Create a file at `Server/BarterShopsMutate/MyMod_Shop_RandomMerchant.json`. The filename must match the base `BarterShop` asset exactly, because the framework uses the filename to find which shop to inject trades into.

```json
{
  "Pools": [
    {
      "SlotCount": 3,
      "InputPool": [
        {
          "ItemId": "MyMod_CurrencyItem_A",
          "Quantity": 5,
          "Stock": 1,
          "Weight": 1.0
        },
        {
          "ItemId": "MyMod_CurrencyItem_B",
          "Quantity": 5,
          "Stock": 1,
          "Weight": 1.0
        },
        {
          "ItemId": "MyMod_CurrencyItem_C",
          "Quantity": 5,
          "Stock": 1,
          "Weight": 1.0
        }
      ],
      "OutputPool": [
        {
          "ItemId": "MyMod_Reward_Sword",
          "Quantity": 1,
          "Stock": 1,
          "Weight": 1.0
        },
        {
          "ItemId": "MyMod_Reward_Bow",
          "Quantity": 1,
          "Stock": 1,
          "Weight": 1.0
        },
        {
          "ItemId": "MyMod_Reward_Staff",
          "Quantity": 1,
          "Stock": 1,
          "Weight": 1.0
        }
      ]
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `SlotCount` | How many random trade slots to generate from this pool |
| `InputPool` | The items the player gives. One is chosen at random per slot |
| `OutputPool` | The items the player receives. One is chosen at random per slot |
| `Quantity` | How many of this item are involved in the trade |
| `Stock` | How many times this trade can be completed before it sells out |
| `Weight` | Relative probability of this item being chosen from the pool |

With `SlotCount: 3` and the above example, the server will generate 3 trades. Each trade randomly pairs one input item with one output item. The same item can appear in multiple slots.

## Using Multiple Pool Configurations

You can have multiple pool objects in the `Pools` array. Each pool is processed independently. This is useful when you want different tiers or categories of trades:

```json
{
  "Pools": [
    {
      "SlotCount": 2,
      "InputPool": [
        { "ItemId": "MyMod_CommonCurrency", "Quantity": 3, "Stock": 1, "Weight": 1.0 },
        { "ItemId": "MyMod_CommonCurrency", "Quantity": 5, "Stock": 1, "Weight": 1.0 }
      ],
      "OutputPool": [
        { "ItemId": "MyMod_CommonReward_A", "Quantity": 1, "Stock": 1, "Weight": 1.0 },
        { "ItemId": "MyMod_CommonReward_B", "Quantity": 1, "Stock": 1, "Weight": 1.0 }
      ]
    },
    {
      "SlotCount": 1,
      "InputPool": [
        { "ItemId": "MyMod_RareCurrency", "Quantity": 1, "Stock": 1, "Weight": 1.0 }
      ],
      "OutputPool": [
        { "ItemId": "MyMod_RareReward_A", "Quantity": 1, "Stock": 1, "Weight": 1.0 },
        { "ItemId": "MyMod_RareReward_B", "Quantity": 1, "Stock": 1, "Weight": 1.0 }
      ]
    }
  ]
}
```

This generates 2 common trades and 1 rare trade each server restart.

## Combining with a Tabbed Shop

A mutating shop can be one of the tabs in a tabbed barter shop. Because the mutating shop injects into the base `BarterShop` asset, it works the same way whether the shop is opened standalone or as a tab. Just reference the same `ShopId` in your tabbed shop asset.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/BarterShops/MyMod_Shop_RandomMerchant.json` | The base barter shop asset (can be empty) |
| `Server/BarterShopsMutate/MyMod_Shop_RandomMerchant.json` | The mutating config that generates random trades on server start |
