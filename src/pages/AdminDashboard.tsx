import { useState, useEffect } from 'react';
import { Users, Package, ShoppingBag, TrendingUp } from 'lucide-react';

export default function AdminDashboard() {
  const [stats, setStats] = useState({ users: 0, products: 0, orders: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await fetch('/api/admin/stats');
        if (res.ok) {
          const data = await res.json();
          setStats(data);
        }
      } catch (error) {
        console.error('Failed to fetch stats', error);
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  if (loading) {
    return <div className="text-gold-400">Loading dashboard...</div>;
  }

  const statCards = [
    { title: 'Total Users', value: stats.users, icon: Users, color: 'text-blue-400' },
    { title: 'Total Products', value: stats.products, icon: Package, color: 'text-green-400' },
    { title: 'Total Orders', value: stats.orders, icon: ShoppingBag, color: 'text-purple-400' },
    { title: 'Revenue', value: '$0.00', icon: TrendingUp, color: 'text-gold-400' },
  ];

  return (
    <div>
      <h1 className="text-3xl font-display text-gold-400 mb-8">Dashboard Overview</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {statCards.map((stat, index) => {
          const Icon = stat.icon;
          return (
            <div key={index} className="glass-gold p-6 rounded-2xl">
              <div className="flex items-center justify-between mb-4">
                <div className={`p-3 rounded-xl bg-surface border border-gold-600/20 ${stat.color}`}>
                  <Icon className="w-6 h-6" />
                </div>
              </div>
              <h3 className="text-gold-100/60 text-sm font-medium uppercase tracking-wider mb-1">{stat.title}</h3>
              <div className="text-3xl font-display text-gold-100">{stat.value}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
