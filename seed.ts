import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function seed() {
  const products = [
    { name: 'Royal Gold Watch', price: 1299.99, category: 'Watches', image: 'https://images.unsplash.com/photo-1523170335258-f5ed11844a49?auto=format&fit=crop&q=80&w=800', description: 'A timeless classic.', stock: 10 },
    { name: 'Diamond Encrusted Ring', price: 3499.99, category: 'Rings', image: 'https://images.unsplash.com/photo-1605100804763-247f6612223e?auto=format&fit=crop&q=80&w=800', description: 'Pure elegance.', stock: 5 },
    { name: 'Golden Chain Necklace', price: 899.99, category: 'Necklaces', image: 'https://images.unsplash.com/photo-1599643478514-4a4e0f69a5a2?auto=format&fit=crop&q=80&w=800', description: 'Solid 24k gold.', stock: 15 },
    { name: 'Sapphire Gold Earrings', price: 1599.99, category: 'Earrings', image: 'https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?auto=format&fit=crop&q=80&w=800', description: 'Deep blue sapphire.', stock: 8 },
    { name: 'Gold Bullion Bar', price: 65000.00, category: 'Investment', image: 'https://images.unsplash.com/photo-1610375461246-83df859d849d?auto=format&fit=crop&q=80&w=800', description: '1kg solid gold bar.', stock: 2 },
    { name: 'Vintage Gold Pocket Watch', price: 4200.00, category: 'Watches', image: 'https://images.unsplash.com/photo-1509048191080-d2984bad6ae5?auto=format&fit=crop&q=80&w=800', description: '19th century piece.', stock: 1 },
  ];

  for (const p of products) {
    await prisma.product.create({ data: p });
  }
  console.log('Seeded products');
}

seed().catch(console.error).finally(() => prisma.$disconnect());
