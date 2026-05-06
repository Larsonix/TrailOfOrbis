---
title: "Tabbed Barter Shops"
order: 10
published: true
draft: false
---

# Tabbed Barter Shops

The tabbed barter shop system lets you create a merchant interface with multiple tabs, where each tab shows a different shop. This is useful when a merchant has many different categories of items to trade and you want to separate them cleanly rather than showing everything in one long list. It uses Hytale's built in Barter system, but allows you to combine multiple shops into one with UI that has tabs like a Workbench.

## How It Works

A tabbed barter shop asset lists multiple tabs. Each tab references an existing `BarterShop` asset and displays an icon and label on the tab button. When the player opens the merchant, they see the tabbed UI and can switch between categories freely.

The tabbed shop is opened from an NPC action. The NPC's action references the tabbed shop asset by ID (the filename without `.json`).

## Step 1 - Create the Individual Shop Assets

Each tab in the tabbed shop corresponds to one `BarterShop` asset. These are standard Hytale barter shop assets that you create as normal. For example, create two shops:

`Server/BarterShops/MyMod_Shop_Weapons.json` and `Server/BarterShops/MyMod_Shop_Armor.json`

These are regular barter shop files with their own trade entries. Refer to Hytale's shop documentation for the full format.

## Step 2 - Create the Tabbed Shop Asset

Create a file at `Server/BarterShopsTabbed/MyMod_MyMerchant.json`:

```json
{
  "DisplayNameKey": "MyMod.barter_tabbed.MyMerchant.title",
  "Tabs": [
    {
      "ShopId": "MyMod_Shop_Weapons",
      "IconTexturePath": "Pages/Icons/MyWeaponIcon.png",
      "LabelKey": "MyMod.barter_tabbed.MyMerchant.weapons.tab"
    },
    {
      "ShopId": "MyMod_Shop_Armor",
      "IconTexturePath": "Pages/Icons/MyArmorIcon.png",
      "LabelKey": "MyMod.barter_tabbed.MyMerchant.armor.tab"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `DisplayNameKey` | Translation key for the merchant's title shown at the top of the UI |
| `Tabs` | Array of tab definitions. Each tab appears as a button in the tab bar |
| `ShopId` | The ID of the `BarterShop` asset to display in this tab. Must match the filename without `.json` |
| `IconTexturePath` | Path to the icon image shown on the tab button. Relative to your asset pack's `Common/UI/` folder |
| `LabelKey` | Translation key for the tab button label |

## Step 3 - Wire It to an NPC

To open the tabbed shop from an NPC, use the `OpenTabbedBarterShop` NPC action in your NPC's dialogue or action chain. The NPC action references the tabbed shop by its asset ID (`MyMod_MyMerchant` in this example).

This is handled through the NPC interaction configuration. In your NPC's role or interaction file, add an action that triggers `OpenTabbedBarterShop` with the shop ID.

## Adding More Tabs

Add more objects to the `Tabs` array to add more tabs. There is no hard limit on the number of tabs, but keep usability in mind. The tab buttons are displayed horizontally, so very long labels or too many tabs can get crowded.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/BarterShopsTabbed/MyMod_MyMerchant.json` | The tabbed shop asset listing all tabs |
| `Server/BarterShops/MyMod_Shop_Weapons.json` | One of the individual shop assets shown in a tab |
| `Server/BarterShops/MyMod_Shop_Armor.json` | Another individual shop asset shown in a different tab |
