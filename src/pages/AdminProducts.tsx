import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { Plus, Edit2, Trash2 } from 'lucide-react';

interface Product {
  id: string;
  name: string;
  price: number;
  category: string;
  stock: number;
  image: string;
}

export default function AdminProducts() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);

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

  useEffect(() => {
    fetchProducts();
  }, []);

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this product?')) return;
    
    try {
      const res = await fetch(`/api/admin/products/${id}`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('Product deleted', {
          style: { background: '#1a1500', color: '#ffd700', border: '1px solid rgba(184,134,11,0.3)' }
        });
        fetchProducts();
      }
    } catch (error) {
      toast.error('Failed to delete product');
    }
  };

  if (loading) return <div className="text-gold-400">Loading products...</div>;

  return (
    <div>
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-display text-gold-400">Product Management</h1>
        <button className="flex items-center gap-2 px-4 py-2 bg-gold-500 text-black rounded-lg font-medium hover:bg-gold-400 transition-colors box-glow-gold">
          <Plus className="w-4 h-4" /> Add Product
        </button>
      </div>
      
      <div className="glass-gold rounded-2xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gold-900/40 border-b border-gold-600/30">
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Product</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Category</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Price</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider">Stock</th>
                <th className="p-4 text-gold-200 font-semibold text-sm uppercase tracking-wider text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gold-600/10">
              {products.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-gold-100/50">No products found.</td>
                </tr>
              ) : (
                products.map((product) => (
                  <tr key={product.id} className="hover:bg-gold-900/20 transition-colors">
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <img src={product.image} alt={product.name} className="w-10 h-10 rounded-md object-cover border border-gold-600/30" />
                        <span className="font-medium text-gold-100">{product.name}</span>
                      </div>
                    </td>
                    <td className="p-4 text-gold-100/70">{product.category}</td>
                    <td className="p-4 text-gold-400 font-medium">${product.price.toFixed(2)}</td>
                    <td className="p-4 text-gold-100/70">{product.stock}</td>
                    <td className="p-4 text-right space-x-2">
                      <button className="p-2 rounded-lg bg-gold-900/30 text-gold-400 hover:bg-gold-800/50 transition-colors border border-gold-600/30">
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button 
                        onClick={() => handleDelete(product.id)}
                        className="p-2 rounded-lg bg-red-950/30 text-red-400 hover:bg-red-900/50 transition-colors border border-red-900/50"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
