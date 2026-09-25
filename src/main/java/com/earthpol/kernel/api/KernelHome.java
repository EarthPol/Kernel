package com.earthpol.kernel.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record KernelHome(
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
