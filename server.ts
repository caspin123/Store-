import express from "express";
import { createServer as createViteServer } from "vite";
import cookieParser from "cookie-parser";
import jwt from "jsonwebtoken";
import { PrismaClient } from "@prisma/client";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const prisma = new PrismaClient();
const JWT_SECRET = process.env.JWT_SECRET || "super-secret-jwt-key";

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());
  app.use(cookieParser());

  // --- Auth Middleware ---
  const authenticate = (req: any, res: any, next: any) => {
    const token = req.cookies.token;
    if (!token) return res.status(401).json({ error: "Unauthorized" });
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      req.user = decoded;
      next();
    } catch (err) {
      res.status(401).json({ error: "Invalid token" });
    }
  };

  const requireAdmin = (req: any, res: any, next: any) => {
    if (req.user?.role !== "admin") {
      return res.status(403).json({ error: "Forbidden: Admins only" });
    }
    next();
  };

  // --- API Routes ---
  
  // 1. Auth / Current User
  app.get("/api/auth/me", authenticate, async (req: any, res: any) => {
    try {
      const user = await prisma.user.findUnique({ where: { id: req.user.id } });
      res.json(user);
    } catch (error) {
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/auth/logout", (req, res) => {
    res.clearCookie("token");
    res.json({ success: true });
  });

  // 2. OAuth Mock Flow (Since we can't easily do real Google OAuth without real credentials)
  // We'll simulate a login for demonstration purposes.
  // In a real app, this would redirect to Google and handle the callback.
  app.post("/api/auth/mock-login", async (req, res) => {
    const { email, name, image } = req.body;
    try {
      let user = await prisma.user.findUnique({ where: { email } });
      
      // HARDCODE rule: wndjdj12@gmail.com is admin
      const role = email === "wndjdj12@gmail.com" ? "admin" : "user";

      if (!user) {
        user = await prisma.user.create({
          data: { email, name, image, role },
        });
      } else if (user.role !== role && email === "wndjdj12@gmail.com") {
        // Ensure the hardcoded admin stays admin
        user = await prisma.user.update({
          where: { email },
          data: { role: "admin" }
        });
      }

      const token = jwt.sign({ id: user.id, email: user.email, role: user.role }, JWT_SECRET, { expiresIn: "7d" });
      
      res.cookie("token", token, {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        maxAge: 7 * 24 * 60 * 60 * 1000,
      });

      res.json({ user });
    } catch (error) {
      console.error(error);
      res.status(500).json({ error: "Login failed" });
    }
  });

  // 3. Admin: Users
  app.get("/api/admin/users", authenticate, requireAdmin, async (req, res) => {
    const users = await prisma.user.findMany({
      orderBy: { createdAt: "desc" }
    });
    res.json(users);
  });

  app.post("/api/admin/users/:id/role", authenticate, requireAdmin, async (req, res) => {
    const { id } = req.params;
    const { role } = req.body;

    try {
      const targetUser = await prisma.user.findUnique({ where: { id } });
      if (!targetUser) return res.status(404).json({ error: "User not found" });

      if (targetUser.email === "wndjdj12@gmail.com" && role !== "admin") {
        return res.status(400).json({ error: "Cannot demote permanent admin" });
      }

      const updatedUser = await prisma.user.update({
        where: { id },
        data: { role }
      });

      res.json(updatedUser);
    } catch (error) {
      res.status(500).json({ error: "Failed to update role" });
    }
  });

  // 4. Admin: Products
  app.get("/api/products", async (req, res) => {
    const products = await prisma.product.findMany({
      orderBy: { createdAt: "desc" }
    });
    res.json(products);
  });

  app.get("/api/products/:id", async (req, res) => {
    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) return res.status(404).json({ error: "Not found" });
    res.json(product);
  });

  app.post("/api/admin/products", authenticate, requireAdmin, async (req, res) => {
    try {
      const product = await prisma.product.create({ data: req.body });
      res.json(product);
    } catch (error) {
      res.status(500).json({ error: "Failed to create product" });
    }
  });

  app.put("/api/admin/products/:id", authenticate, requireAdmin, async (req, res) => {
    try {
      const product = await prisma.product.update({
        where: { id: req.params.id },
        data: req.body
      });
      res.json(product);
    } catch (error) {
      res.status(500).json({ error: "Failed to update product" });
    }
  });

  app.delete("/api/admin/products/:id", authenticate, requireAdmin, async (req, res) => {
    try {
      await prisma.product.delete({ where: { id: req.params.id } });
      res.json({ success: true });
    } catch (error) {
      res.status(500).json({ error: "Failed to delete product" });
    }
  });

  // 5. Admin: Stats
  app.get("/api/admin/stats", authenticate, requireAdmin, async (req, res) => {
    const [usersCount, productsCount, ordersCount] = await Promise.all([
      prisma.user.count(),
      prisma.product.count(),
      prisma.order.count()
    ]);
    res.json({ users: usersCount, products: productsCount, orders: ordersCount });
  });

  // --- Vite Middleware ---
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*all', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
