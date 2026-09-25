package com.earthpol.kernel.data.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record HomeRecord(
    String name,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }
}
