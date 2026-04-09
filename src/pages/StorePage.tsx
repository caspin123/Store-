import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import ProductCard from '../components/ProductCard';

interface Product {
  id: string;
  name: string;
  price: number;
  image: string;
  category: string;
}

export default function StorePage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState('All');

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        const res = await fetch('/api/products');
        if (res.ok) {
          const data = await res.json();
          setProducts(data);
        }
      } catch (error) {
        console.error('Failed to fetch products', error);
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, []);

  const categories = ['All', ...Array.from(new Set(products.map(p => p.category)))];
  
  const filteredProducts = activeCategory === 'All' 
    ? products 
    : products.filter(p => p.category === activeCategory);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-16">
        <h1 className="text-5xl md:text-7xl font-display text-gold-400 mb-6 text-glow-gold">The Collection</h1>
        <p className="text-xl text-gold-100/70 max-w-2xl mx-auto">
          Discover our meticulously curated selection of premium golden artifacts and luxury pieces.
        </p>
      </div>

      {/* Category Nav */}
      <div className="flex justify-center flex-wrap gap-4 mb-16">
        {categories.map(category => (
          <button
            key={category}
            onClick={() => setActiveCategory(category)}
            className={`relative px-6 py-2 text-sm uppercase tracking-wider transition-colors ${
              activeCategory === category ? 'text-gold-400 font-bold' : 'text-gold-100/60 hover:text-gold-200'
            }`}
          >
            {category}
            {activeCategory === category && (
              <motion.div 
                layoutId="activeCategory"
                className="absolute bottom-0 left-0 right-0 h-0.5 bg-gold-400 box-glow-gold"
              />
            )}
          </button>
        ))}
      </div>

      {/* Product Grid */}
      {loading ? (
        <div className="flex justify-center py-20">
          <div className="w-12 h-12 border-4 border-gold-600/30 border-t-gold-400 rounded-full animate-spin"></div>
        </div>
      ) : filteredProducts.length === 0 ? (
        <div className="text-center py-20 text-gold-100/60 text-lg">
          No products found in this category.
        </div>
      ) : (
        <motion.div 
          layout
          className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8"
        >
          {filteredProducts.map(product => (
            <ProductCard key={product.id} product={product} />
          ))}
        </motion.div>
      )}
    </div>
  );
}
