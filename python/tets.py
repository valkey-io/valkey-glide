import matplotlib

matplotlib.use("Agg")  # Headless backend

import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection


def draw_box(ax, origin, size, color="gray", alpha=0.6, label=""):
    x, y, z = origin
    dx, dy, dz = size

    corners = [
        [x, y, z],
        [x + dx, y, z],
        [x + dx, y + dy, z],
        [x, y + dy, z],
        [x, y, z + dz],
        [x + dx, y, z + dz],
        [x + dx, y + dy, z + dz],
        [x, y + dy, z + dz],
    ]

    faces = [
        [corners[0], corners[1], corners[5], corners[4]],  # Front
        [corners[7], corners[6], corners[2], corners[3]],  # Back
        [corners[0], corners[3], corners[7], corners[4]],  # Left
        [corners[1], corners[2], corners[6], corners[5]],  # Right
        [corners[0], corners[1], corners[2], corners[3]],  # Bottom
        [corners[4], corners[5], corners[6], corners[7]],  # Top
    ]

    ax.add_collection3d(
        Poly3DCollection(
            faces, facecolors=color, linewidths=1, edgecolors="k", alpha=alpha
        )
    )

    if label:
        ax.text(
            x + dx / 2,
            y - 2,
            z + dz / 2,
            label,
            fontsize=9,
            color="black",
            weight="bold",
            horizontalalignment="center",
            verticalalignment="center",
        )


fig = plt.figure(figsize=(12, 10))
ax = fig.add_subplot(111, projection="3d")

# --- Dimensions (cm) ---
width_ext = 28
depth = 51
height = 51.5
thickness = 1.7
stretcher_depth = 10

drawer_x = thickness + 1.3
drawer_width = width_ext - 2 * thickness - 2.6
drawer_depth_internal = 50
drawer_front_y_pos = 0.5

# UPDATED HANDLE SIZE
handle_w = 18
handle_h = 2
handle_d = 2.5

# --- 1. Cabinet Frame ---
draw_box(ax, (0, 0, 0), (thickness, depth, height), color="#e0e0e0")
draw_box(ax, (width_ext - thickness, 0, 0), (thickness, depth, height), color="#e0e0e0")
draw_box(
    ax,
    (thickness, 0, 0),
    (width_ext - 2 * thickness, depth, thickness),
    color="#bfbfbf",
)

stretcher_z = height - thickness
draw_box(
    ax,
    (thickness, 0, stretcher_z),
    (width_ext - 2 * thickness, stretcher_depth, thickness),
    color="#ff6666",
    alpha=0.9,
    label="Stretcher",
)
draw_box(
    ax,
    (thickness, depth - stretcher_depth, stretcher_z),
    (width_ext - 2 * thickness, stretcher_depth, thickness),
    color="#ff6666",
    alpha=0.9,
)

# --- 2. Bottom Drawer (28cm) ---
bottom_drawer_z = 2
bottom_drawer_height = 28
draw_box(
    ax,
    (drawer_x, drawer_front_y_pos, bottom_drawer_z),
    (drawer_width, drawer_depth_internal, bottom_drawer_height),
    color="#87CEEB",
    alpha=0.9,
    label="Tall Bottom\n(28cm)",
)

handle_x = drawer_x + (drawer_width - handle_w) / 2
handle_z_bottom = bottom_drawer_z + (bottom_drawer_height - handle_h) / 2
draw_box(
    ax,
    (handle_x, drawer_front_y_pos - handle_d, handle_z_bottom),
    (handle_w, handle_d, handle_h),
    color="#333333",
    alpha=1.0,
)

# --- 3. Top Drawer (12cm) ---
top_drawer_z = 32
top_drawer_height = 12
draw_box(
    ax,
    (drawer_x, drawer_front_y_pos, top_drawer_z),
    (drawer_width, drawer_depth_internal, top_drawer_height),
    color="#87CEEB",
    alpha=0.9,
    label="Top\n(12cm)",
)

handle_z_top = top_drawer_z + (top_drawer_height - handle_h) / 2
draw_box(
    ax,
    (handle_x, drawer_front_y_pos - handle_d, handle_z_top),
    (handle_w, handle_d, handle_h),
    color="#333333",
    alpha=1.0,
)

# --- Plot Settings ---
ax.set_xlabel("Width (cm)")
ax.set_ylabel("Depth (cm)")
ax.set_zlabel("Height (cm)")
ax.set_xlim(0, 30)
ax.set_ylim(-5, 55)
ax.set_zlim(0, 60)
ax.set_title("Final Design: Clear Gap & Long Handles")
ax.view_init(elev=10, azim=-25)

plt.tight_layout()
plt.savefig("final_design_clear_gap_long_handles.png", dpi=200)
plt.close(fig)

print("Saved final_design_clear_gap_long_handles.png")
