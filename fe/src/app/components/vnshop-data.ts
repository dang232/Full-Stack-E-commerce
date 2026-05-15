export interface Product {
  id: string;
  name: string;
  nameEn: string;
  price: number;
  originalPrice?: number;
  discount?: number;
  image: string;
  images: string[];
  category: string;
  categoryLabel: string;
  sellerId: string;
  sellerName: string;
  rating: number;
  reviewCount: number;
  sold: number;
  stock: number;
  description: string;
  badge?: "flash" | "new" | "bestseller" | "hot";
  colors?: string[];
  sizes?: string[];
  shipping: string;
  shippingFee: number;
  location: string;
  tags: string[];
}

export interface Seller {
  id: string;
  name: string;
  avatar: string;
  banner: string;
  rating: number;
  followers: number;
  products: number;
  sales: number;
  category: string;
  verified: boolean;
  responseRate: number;
  joinedYear: number;
  location: string;
  description: string;
}

export interface Category {
  id: string;
  label: string;
  emoji: string;
  count: number;
  color: string;
}

export interface Order {
  id: string;
  date: string;
  status: "pending" | "confirmed" | "shipping" | "delivered" | "cancelled" | "returned";
  items: { productId: string; name: string; image: string; quantity: number; price: number; variant?: string }[];
  total: number;
  shipping: number;
  discount: number;
  address: string;
  trackingCode?: string;
  seller: string;
  paymentMethod: string;
  estimatedDelivery?: string;
}

export interface Review {
  id: string;
  productId: string;
  userId: string;
  userName: string;
  avatar: string;
  rating: number;
  comment: string;
  date: string;
  images?: string[];
  helpful: number;
  variant?: string;
}

export const categories: Category[] = [
  { id: "electronics", label: "Điện Tử", emoji: "💻", count: 1240, color: "#3B82F6" },
  { id: "fashion", label: "Thời Trang", emoji: "👗", count: 3560, color: "#EC4899" },
  { id: "beauty", label: "Làm Đẹp", emoji: "💄", count: 890, color: "#F59E0B" },
  { id: "home", label: "Nhà Cửa", emoji: "🏠", count: 670, color: "#10B981" },
  { id: "sports", label: "Thể Thao", emoji: "⚽", count: 420, color: "#6366F1" },
  { id: "books", label: "Sách & Văn Phòng", emoji: "📚", count: 310, color: "#8B5CF6" },
  { id: "food", label: "Thực Phẩm", emoji: "🍜", count: 580, color: "#EF4444" },
  { id: "baby", label: "Mẹ & Bé", emoji: "👶", count: 290, color: "#F97316" },
];

export const sellers: Seller[] = [
  {
    id: "s1",
    name: "TechZone Vietnam",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80",
    banner: "https://images.unsplash.com/photo-1487014679447-9f8336841d58?w=800&q=80",
    rating: 4.9,
    followers: 128500,
    products: 342,
    sales: 89420,
    category: "electronics",
    verified: true,
    responseRate: 98,
    joinedYear: 2019,
    location: "Hà Nội",
    description: "Chuyên kinh doanh thiết bị điện tử chính hãng, bảo hành 12 tháng"
  },
  {
    id: "s2",
    name: "Fashion House VN",
    avatar: "https://images.unsplash.com/photo-1581065178047-8ee15951ede6?w=200&q=80",
    banner: "https://images.unsplash.com/photo-1523381294911-8d3cead13475?w=800&q=80",
    rating: 4.8,
    followers: 95300,
    products: 890,
    sales: 156000,
    category: "fashion",
    verified: true,
    responseRate: 95,
    joinedYear: 2020,
    location: "TP. Hồ Chí Minh",
    description: "Thời trang cao cấp dành cho phụ nữ hiện đại, phong cách Âu - Á"
  },
  {
    id: "s3",
    name: "Beauty Corner",
    avatar: "https://images.unsplash.com/photo-1589525231707-f2de2428f59c?w=200&q=80",
    banner: "https://images.unsplash.com/photo-1580870069867-74c57ee1bb07?w=800&q=80",
    rating: 4.9,
    followers: 72100,
    products: 234,
    sales: 67800,
    category: "beauty",
    verified: true,
    responseRate: 99,
    joinedYear: 2021,
    location: "Đà Nẵng",
    description: "Mỹ phẩm thuần chay, skincare cao cấp nhập khẩu chính hãng"
  },
  {
    id: "s4",
    name: "SportPro VN",
    avatar: "https://images.unsplash.com/photo-1522529599102-193c0d76b5b6?w=200&q=80",
    banner: "https://images.unsplash.com/photo-1625860191460-10a66c7384fb?w=800&q=80",
    rating: 4.7,
    followers: 43200,
    products: 178,
    sales: 34500,
    category: "sports",
    verified: true,
    responseRate: 92,
    joinedYear: 2020,
    location: "Hà Nội",
    description: "Giày thể thao và dụng cụ thể thao chính hãng"
  },
  {
    id: "s5",
    name: "BookWorld VN",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80",
    banner: "https://images.unsplash.com/photo-1487014679447-9f8336841d58?w=800&q=80",
    rating: 5.0,
    followers: 28900,
    products: 1240,
    sales: 98700,
    category: "books",
    verified: true,
    responseRate: 97,
    joinedYear: 2018,
    location: "Hà Nội",
    description: "Nhà sách uy tín hàng đầu, sách giáo khoa và sách nghiệp vụ"
  },
];

export const products: Product[] = [
  {
    id: "p1",
    name: "Tai Nghe Không Dây Sony WH-1000XM5",
    nameEn: "Sony WH-1000XM5 Wireless Headphones",
    price: 4990000,
    originalPrice: 6500000,
    discount: 23,
    image: "https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46?w=800&q=80",
      "https://images.unsplash.com/photo-1603351154351-5e2d0600bb77?w=800&q=80",
      "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.9,
    reviewCount: 2841,
    sold: 15420,
    stock: 87,
    description: "Tai nghe không dây chống ồn chủ động hàng đầu từ Sony. Âm thanh Hi-Res Audio, pin 30 giờ, chất lượng call đỉnh cao với 8 mic. Gấp gọn tiện lợi khi di chuyển.",
    badge: "bestseller",
    colors: ["Đen", "Bạc Platinum"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["sony", "headphones", "chống ồn", "bluetooth"]
  },
  {
    id: "p2",
    name: "Giày Thể Thao Nike Air Max 270",
    nameEn: "Nike Air Max 270 Sneakers",
    price: 2350000,
    originalPrice: 3200000,
    discount: 27,
    image: "https://images.unsplash.com/photo-1625860191460-10a66c7384fb?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1625860191460-10a66c7384fb?w=800&q=80",
      "https://images.unsplash.com/photo-1632497775897-815042a13216?w=800&q=80",
      "https://images.unsplash.com/photo-1710553455491-482fa1751dc4?w=800&q=80",
    ],
    category: "sports",
    categoryLabel: "Thể Thao",
    sellerId: "s4",
    sellerName: "SportPro VN",
    rating: 4.8,
    reviewCount: 1234,
    sold: 8920,
    stock: 45,
    description: "Giày thể thao Nike Air Max 270 với đế Air Unit lớn nhất từ trước đến nay trong dòng lifestyle. Siêu nhẹ, cực êm, phong cách không giới hạn.",
    badge: "hot",
    colors: ["Trắng/Đen", "Xanh Navy", "Đỏ/Trắng"],
    sizes: ["38", "39", "40", "41", "42", "43", "44"],
    shipping: "J&T Express",
    shippingFee: 30000,
    location: "TP. Hồ Chí Minh",
    tags: ["nike", "giày thể thao", "running", "sneakers"]
  },
  {
    id: "p3",
    name: "Áo Thun Cotton Premium Unisex",
    nameEn: "Premium Cotton Unisex T-Shirt",
    price: 299000,
    originalPrice: 450000,
    discount: 34,
    image: "https://images.unsplash.com/photo-1581655353564-df123a1eb820?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1581655353564-df123a1eb820?w=800&q=80",
      "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=800&q=80",
      "https://images.unsplash.com/photo-1523381294911-8d3cead13475?w=800&q=80",
    ],
    category: "fashion",
    categoryLabel: "Thời Trang",
    sellerId: "s2",
    sellerName: "Fashion House VN",
    rating: 4.7,
    reviewCount: 567,
    sold: 12800,
    stock: 234,
    description: "Áo thun cotton 100% cao cấp, thoáng mát, co giãn 4 chiều. Dễ phối đồ, phù hợp nhiều phong cách. Wash được máy, không phai màu.",
    badge: "bestseller",
    colors: ["Trắng", "Đen", "Xám", "Navy", "Xanh Lá"],
    sizes: ["XS", "S", "M", "L", "XL", "XXL"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "TP. Hồ Chí Minh",
    tags: ["áo thun", "cotton", "unisex", "basic"]
  },
  {
    id: "p4",
    name: "Đồng Hồ Thông Minh Samsung Galaxy Watch 6",
    nameEn: "Samsung Galaxy Watch 6 Smartwatch",
    price: 4500000,
    originalPrice: 6990000,
    discount: 36,
    image: "https://images.unsplash.com/photo-1689287428096-7e1dcc705a5c?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1689287428096-7e1dcc705a5c?w=800&q=80",
      "https://images.unsplash.com/photo-1657235895095-e043ce2ebf41?w=800&q=80",
      "https://images.unsplash.com/photo-1561634370-e284d2c11cf8?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.8,
    reviewCount: 892,
    sold: 4320,
    stock: 62,
    description: "Samsung Galaxy Watch 6 - Đồng hồ thông minh cao cấp với màn hình AMOLED sắc nét, theo dõi sức khỏe toàn diện, chống nước 5ATM, pin 40 giờ.",
    badge: "flash",
    colors: ["Graphite", "Gold", "Silver"],
    sizes: ["40mm", "44mm"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["samsung", "smartwatch", "wearable", "thể thao"]
  },
  {
    id: "p5",
    name: "Kem Dưỡng Da Vitamin C Glow 30ml",
    nameEn: "Vitamin C Glow Moisturizer 30ml",
    price: 450000,
    originalPrice: 680000,
    discount: 34,
    image: "https://images.unsplash.com/photo-1580870069867-74c57ee1bb07?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1580870069867-74c57ee1bb07?w=800&q=80",
      "https://images.unsplash.com/photo-1585945037805-5fd82c2e60b1?w=800&q=80",
      "https://images.unsplash.com/photo-1608068811588-3a67006b7489?w=800&q=80",
    ],
    category: "beauty",
    categoryLabel: "Làm Đẹp",
    sellerId: "s3",
    sellerName: "Beauty Corner",
    rating: 4.9,
    reviewCount: 3210,
    sold: 28900,
    stock: 156,
    description: "Kem dưỡng ẩm Vitamin C 20% cô đặc, làm sáng da, mờ thâm nám, chống oxy hóa mạnh mẽ. Công thức thuần chay, không paraben, phù hợp mọi loại da.",
    badge: "bestseller",
    colors: [],
    shipping: "Giao Hàng Tiết Kiệm",
    shippingFee: 20000,
    location: "Đà Nẵng",
    tags: ["skincare", "vitamin c", "dưỡng da", "làm sáng da"]
  },
  {
    id: "p6",
    name: "Laptop ASUS VivoBook 15 OLED",
    nameEn: "ASUS VivoBook 15 OLED Laptop",
    price: 18500000,
    originalPrice: 22990000,
    discount: 20,
    image: "https://images.unsplash.com/photo-1487014679447-9f8336841d58?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1487014679447-9f8336841d58?w=800&q=80",
      "https://images.unsplash.com/photo-1526406915894-7bcd65f60845?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.7,
    reviewCount: 456,
    sold: 2340,
    stock: 23,
    description: "Laptop ASUS VivoBook 15 màn hình OLED 15.6 inch FHD, chip Intel Core i5-12500H, RAM 16GB, SSD 512GB, pin 70Wh, trọng lượng 1.7kg.",
    badge: "new",
    colors: ["Midnight Black", "Cool Silver"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["laptop", "asus", "vivobook", "oled", "sinh viên"]
  },
  {
    id: "p7",
    name: "Apple AirPods Pro (Thế Hệ 2)",
    nameEn: "Apple AirPods Pro 2nd Generation",
    price: 5900000,
    originalPrice: 6990000,
    discount: 16,
    image: "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.9,
    reviewCount: 1890,
    sold: 7890,
    stock: 34,
    description: "AirPods Pro thế hệ 2 với chip H2, chống ồn ANC thế hệ mới, âm thanh Adaptive Audio, chế độ Transparency thích ứng, pin 6 giờ + hộp sạc 30 giờ.",
    badge: "hot",
    colors: ["Trắng"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["apple", "airpods", "earbuds", "chống ồn"]
  },
  {
    id: "p8",
    name: "Quần Jean Slim Fit Nam Cao Cấp",
    nameEn: "Premium Slim Fit Men's Jeans",
    price: 599000,
    originalPrice: 890000,
    discount: 33,
    image: "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=800&q=80",
    ],
    category: "fashion",
    categoryLabel: "Thời Trang",
    sellerId: "s2",
    sellerName: "Fashion House VN",
    rating: 4.6,
    reviewCount: 789,
    sold: 5670,
    stock: 89,
    description: "Quần jean nam denim co giãn 5%, form slim fit ôm nhẹ tôn dáng. Chất liệu bền đẹp, wash bạc tự nhiên, phù hợp đi làm và đi chơi.",
    badge: undefined,
    colors: ["Xanh đậm", "Xanh bạc", "Đen"],
    sizes: ["28", "29", "30", "31", "32", "33", "34"],
    shipping: "J&T Express",
    shippingFee: 25000,
    location: "TP. Hồ Chí Minh",
    tags: ["quần jean", "nam", "slim fit", "denim"]
  },
  {
    id: "p9",
    name: "Máy Lọc Không Khí Xiaomi Mi 4 Pro",
    nameEn: "Xiaomi Mi Air Purifier 4 Pro",
    price: 3200000,
    originalPrice: 4500000,
    discount: 29,
    image: "https://images.unsplash.com/photo-1591869754715-5f679687039c?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1591869754715-5f679687039c?w=800&q=80",
    ],
    category: "home",
    categoryLabel: "Nhà Cửa",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.7,
    reviewCount: 634,
    sold: 3210,
    stock: 45,
    description: "Máy lọc không khí Xiaomi Mi 4 Pro, lọc 99.97% bụi mịn PM2.5, khử mùi hiệu quả, điều khiển qua app. Diện tích phủ 60m², lưu lượng gió 500m³/h.",
    badge: "flash",
    colors: ["Trắng"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["xiaomi", "máy lọc không khí", "smarthome", "không khí sạch"]
  },
  {
    id: "p10",
    name: "Bình Giữ Nhiệt Stanley Quencher 1.18L",
    nameEn: "Stanley Quencher Tumbler 1.18L",
    price: 890000,
    originalPrice: 1200000,
    discount: 26,
    image: "https://images.unsplash.com/photo-1608068811588-3a67006b7489?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1608068811588-3a67006b7489?w=800&q=80",
    ],
    category: "home",
    categoryLabel: "Nhà Cửa",
    sellerId: "s4",
    sellerName: "SportPro VN",
    rating: 4.8,
    reviewCount: 2109,
    sold: 11200,
    stock: 78,
    description: "Bình giữ nhiệt Stanley Quencher thép không gỉ, giữ lạnh 36 giờ, giữ nóng 8 giờ. Tay cầm tiện lợi, miệng rộng dễ đổ đá. Hàng chính hãng.",
    badge: "bestseller",
    colors: ["Rose Quartz", "Charcoal", "Cream", "Jade"],
    shipping: "Giao Hàng Tiết Kiệm",
    shippingFee: 25000,
    location: "TP. Hồ Chí Minh",
    tags: ["stanley", "bình giữ nhiệt", "tumbler", "lifestyle"]
  },
  {
    id: "p11",
    name: "Nước Hoa Chanel Bleu de Chanel EDP",
    nameEn: "Chanel Bleu de Chanel EDP",
    price: 3500000,
    originalPrice: 4200000,
    discount: 17,
    image: "https://images.unsplash.com/photo-1585945037805-5fd82c2e60b1?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1585945037805-5fd82c2e60b1?w=800&q=80",
    ],
    category: "beauty",
    categoryLabel: "Làm Đẹp",
    sellerId: "s3",
    sellerName: "Beauty Corner",
    rating: 4.9,
    reviewCount: 945,
    sold: 4560,
    stock: 31,
    description: "Nước hoa Chanel Bleu de Chanel EDP 100ml - mùi hương gỗ thơm đặc trưng, thanh lịch và nam tính. Hương đầu: chanh, bưởi. Hương giữa: gừng, tiêu. Hương cuối: đàn hương.",
    badge: undefined,
    sizes: ["50ml", "100ml"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Đà Nẵng",
    tags: ["chanel", "nước hoa", "perfume", "nam"]
  },
  {
    id: "p12",
    name: "Bàn Phím Cơ Gaming Keychron K2 Pro",
    nameEn: "Keychron K2 Pro Mechanical Keyboard",
    price: 1650000,
    originalPrice: 2100000,
    discount: 21,
    image: "https://images.unsplash.com/photo-1609334761848-77b4d1994040?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1609334761848-77b4d1994040?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.8,
    reviewCount: 678,
    sold: 3450,
    stock: 56,
    description: "Bàn phím cơ Keychron K2 Pro, layout 75%, switch Gateron G Pro, đèn RGB, kết nối đa thiết bị Bluetooth 5.1, tương thích Windows & Mac.",
    badge: "new",
    colors: ["Carbon Black", "White"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["bàn phím cơ", "keychron", "gaming", "mechanical keyboard"]
  },
  {
    id: "p13",
    name: "Sách Đắc Nhân Tâm - Dale Carnegie",
    nameEn: "How to Win Friends and Influence People",
    price: 85000,
    originalPrice: 120000,
    discount: 29,
    image: "https://images.unsplash.com/photo-1526406915894-7bcd65f60845?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1526406915894-7bcd65f60845?w=800&q=80",
    ],
    category: "books",
    categoryLabel: "Sách & Văn Phòng",
    sellerId: "s5",
    sellerName: "BookWorld VN",
    rating: 5.0,
    reviewCount: 8920,
    sold: 67800,
    stock: 500,
    description: "Cuốn sách kinh điển về kỹ năng giao tiếp và quan hệ con người. Hơn 30 triệu bản bán ra toàn thế giới. Bản dịch Vũ Trọng Đại, NXB Tổng Hợp TPHCM.",
    badge: "bestseller",
    colors: [],
    shipping: "Giao Hàng Tiết Kiệm",
    shippingFee: 15000,
    location: "Hà Nội",
    tags: ["sách", "kỹ năng mềm", "dale carnegie", "bestseller"]
  },
  {
    id: "p14",
    name: "Áo Khoác Bomber Unisex Premium",
    nameEn: "Premium Unisex Bomber Jacket",
    price: 850000,
    originalPrice: 1200000,
    discount: 29,
    image: "https://images.unsplash.com/photo-1523381294911-8d3cead13475?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1523381294911-8d3cead13475?w=800&q=80",
    ],
    category: "fashion",
    categoryLabel: "Thời Trang",
    sellerId: "s2",
    sellerName: "Fashion House VN",
    rating: 4.7,
    reviewCount: 432,
    sold: 3450,
    stock: 67,
    description: "Áo khoác bomber unisex chất liệu polyester cao cấp, lớp lót ấm, phản quang nhẹ. Form vừa vặn, phù hợp mọi vóc dáng. Phong cách streetwear hiện đại.",
    badge: "hot",
    colors: ["Đen", "Xanh Rêu", "Be"],
    sizes: ["S", "M", "L", "XL"],
    shipping: "J&T Express",
    shippingFee: 30000,
    location: "TP. Hồ Chí Minh",
    tags: ["áo khoác", "bomber", "unisex", "streetwear"]
  },
  {
    id: "p15",
    name: "Set Chăm Sóc Da Nam 5 Món",
    nameEn: "Men's Skincare Set 5 Products",
    price: 750000,
    originalPrice: 1100000,
    discount: 32,
    image: "https://images.unsplash.com/photo-1620783770629-122b7f187703?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1620783770629-122b7f187703?w=800&q=80",
    ],
    category: "beauty",
    categoryLabel: "Làm Đẹp",
    sellerId: "s3",
    sellerName: "Beauty Corner",
    rating: 4.8,
    reviewCount: 1234,
    sold: 8760,
    stock: 89,
    description: "Set chăm sóc da nam đầy đủ gồm: sữa rửa mặt, toner, serum vitamin C, kem dưỡng ẩm và kem chống nắng SPF50. Phù hợp mọi loại da, không kích ứng.",
    badge: "flash",
    colors: [],
    shipping: "Giao Hàng Tiết Kiệm",
    shippingFee: 20000,
    location: "Đà Nẵng",
    tags: ["skincare nam", "set dưỡng da", "vitamin c", "chống nắng"]
  },
  {
    id: "p16",
    name: "Màn Hình Gaming LG UltraWide 34 inch",
    nameEn: "LG UltraWide 34\" Gaming Monitor",
    price: 12500000,
    originalPrice: 16500000,
    discount: 24,
    image: "https://images.unsplash.com/photo-1620783770629-122b7f187703?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1620783770629-122b7f187703?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.8,
    reviewCount: 234,
    sold: 890,
    stock: 18,
    description: "Màn hình gaming LG UltraWide 34\" WQHD IPS 144Hz, 1ms, HDR10, G-Sync/FreeSync Premium, cong 1500R. Hoàn hảo cho gaming và làm việc đa nhiệm.",
    badge: "new",
    colors: ["Đen"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["màn hình", "gaming", "lg", "ultrawide"]
  },
  {
    id: "p17",
    name: "Giày Sandal Nữ Da Thật Mũi Nhọn",
    nameEn: "Women's Genuine Leather Pointed Sandals",
    price: 420000,
    originalPrice: 650000,
    discount: 35,
    image: "https://images.unsplash.com/photo-1632497775897-815042a13216?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1632497775897-815042a13216?w=800&q=80",
    ],
    category: "fashion",
    categoryLabel: "Thời Trang",
    sellerId: "s2",
    sellerName: "Fashion House VN",
    rating: 4.6,
    reviewCount: 345,
    sold: 4560,
    stock: 123,
    description: "Giày sandal nữ da thật mũi nhọn, gót vuông 5cm, êm chân, dễ phối đồ. Phù hợp đi làm, đi chơi, tiệc tùng. Kích cỡ từ 35-40.",
    badge: undefined,
    colors: ["Đen", "Nâu", "Kem"],
    sizes: ["35", "36", "37", "38", "39", "40"],
    shipping: "J&T Express",
    shippingFee: 25000,
    location: "TP. Hồ Chí Minh",
    tags: ["giày sandal nữ", "da thật", "gót vuông", "thời trang"]
  },
  {
    id: "p18",
    name: "Máy Ảnh Sony Alpha A6400 Kit 16-50mm",
    nameEn: "Sony Alpha A6400 Mirrorless Camera Kit",
    price: 22000000,
    originalPrice: 27000000,
    discount: 19,
    image: "https://images.unsplash.com/photo-1591869754715-5f679687039c?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1591869754715-5f679687039c?w=800&q=80",
    ],
    category: "electronics",
    categoryLabel: "Điện Tử",
    sellerId: "s1",
    sellerName: "TechZone Vietnam",
    rating: 4.9,
    reviewCount: 567,
    sold: 1230,
    stock: 12,
    description: "Máy ảnh Sony Alpha A6400 không gương lật, cảm biến 24.2MP APS-C, lấy nét theo dõi mắt realtime, quay video 4K 30fps, màn hình lật 180°. Kèm lens kit 16-50mm.",
    badge: undefined,
    colors: ["Đen"],
    shipping: "Giao Hàng Nhanh",
    shippingFee: 0,
    location: "Hà Nội",
    tags: ["sony", "máy ảnh", "mirrorless", "a6400", "nhiếp ảnh"]
  },
  {
    id: "p19",
    name: "Cốc Gốm Handmade Hội An",
    nameEn: "Hoi An Handmade Ceramic Mug",
    price: 150000,
    originalPrice: 200000,
    discount: 25,
    image: "https://images.unsplash.com/photo-1608068811588-3a67006b7489?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1608068811588-3a67006b7489?w=800&q=80",
    ],
    category: "home",
    categoryLabel: "Nhà Cửa",
    sellerId: "s4",
    sellerName: "SportPro VN",
    rating: 4.8,
    reviewCount: 890,
    sold: 9870,
    stock: 234,
    description: "Cốc gốm sứ thủ công Hội An, dáng trụ đơn giản, men satin mịn màng. Dung tích 350ml, an toàn thực phẩm, có thể rửa máy. Món quà tặng ý nghĩa.",
    badge: "new",
    colors: ["Xanh lam", "Trắng ngà", "Xám khói", "Hồng đất"],
    shipping: "Giao Hàng Tiết Kiệm",
    shippingFee: 25000,
    location: "Quảng Nam",
    tags: ["gốm sứ", "handmade", "hội an", "quà tặng"]
  },
  {
    id: "p20",
    name: "Giày Chạy Bộ Adidas Ultraboost 22",
    nameEn: "Adidas Ultraboost 22 Running Shoes",
    price: 3200000,
    originalPrice: 4500000,
    discount: 29,
    image: "https://images.unsplash.com/photo-1710553455491-482fa1751dc4?w=600&q=80",
    images: [
      "https://images.unsplash.com/photo-1710553455491-482fa1751dc4?w=800&q=80",
    ],
    category: "sports",
    categoryLabel: "Thể Thao",
    sellerId: "s4",
    sellerName: "SportPro VN",
    rating: 4.9,
    reviewCount: 1567,
    sold: 6780,
    stock: 38,
    description: "Giày chạy bộ Adidas Ultraboost 22 với công nghệ đệm BOOST cải tiến, upper Primeknit co giãn, đế ngoài Continental cao su chống trượt mọi địa hình.",
    badge: "hot",
    colors: ["Trắng/Xanh", "Đen/Vàng", "Xám/Hồng"],
    sizes: ["38", "39", "40", "41", "42", "43", "44"],
    shipping: "J&T Express",
    shippingFee: 30000,
    location: "TP. Hồ Chí Minh",
    tags: ["adidas", "ultraboost", "giày chạy bộ", "running"]
  },
];

export const flashSaleProducts = products.filter(p => p.badge === "flash" || p.discount! >= 30).slice(0, 6);

export const reviews: Review[] = [
  {
    id: "r1",
    productId: "p1",
    userId: "u1",
    userName: "Nguyễn Minh Tuấn",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=80&q=80",
    rating: 5,
    comment: "Tai nghe quá xịn! Âm thanh tuyệt vời, chống ồn rất hiệu quả, đội cả ngày không đau tai. Xứng đáng với giá tiền. Giao hàng nhanh, đóng gói cẩn thận.",
    date: "2026-04-15",
    images: ["https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46?w=200&q=80"],
    helpful: 234,
    variant: "Đen"
  },
  {
    id: "r2",
    productId: "p1",
    userId: "u2",
    userName: "Trần Thị Lan Anh",
    avatar: "https://images.unsplash.com/photo-1581065178047-8ee15951ede6?w=80&q=80",
    rating: 4,
    comment: "Sản phẩm chính hãng 100%, app Sony Connect kết nối dễ dàng. Chỉ tiếc pin không giữ được như quảng cáo nhưng vẫn rất ổn với 24-26 tiếng.",
    date: "2026-04-10",
    helpful: 89,
    variant: "Bạc Platinum"
  },
  {
    id: "r3",
    productId: "p1",
    userId: "u3",
    userName: "Lê Văn Hải",
    avatar: "https://images.unsplash.com/photo-1589525231707-f2de2428f59c?w=80&q=80",
    rating: 5,
    comment: "Mình đã dùng XM4 trước đó, XM5 cải tiến nhiều lắm. Âm bass sâu hơn, treble trong hơn, form đội nhẹ hơn. Rất recommend cho ai cần nghe nhạc chất lượng cao!",
    date: "2026-04-02",
    helpful: 156,
    variant: "Đen"
  },
];

export const sampleOrders: Order[] = [
  {
    id: "ORD-2026-001",
    date: "2026-05-10",
    status: "shipping",
    items: [
      { productId: "p1", name: "Tai Nghe Sony WH-1000XM5", image: "https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46?w=80&q=80", quantity: 1, price: 4990000, variant: "Đen" }
    ],
    total: 4990000,
    shipping: 0,
    discount: 0,
    address: "123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
    trackingCode: "GHN123456789VN",
    seller: "TechZone Vietnam",
    paymentMethod: "VNPay",
    estimatedDelivery: "2026-05-14"
  },
  {
    id: "ORD-2026-002",
    date: "2026-05-05",
    status: "delivered",
    items: [
      { productId: "p3", name: "Áo Thun Cotton Premium", image: "https://images.unsplash.com/photo-1581655353564-df123a1eb820?w=80&q=80", quantity: 2, price: 299000, variant: "Trắng - M" },
      { productId: "p5", name: "Kem Dưỡng Da Vitamin C", image: "https://images.unsplash.com/photo-1580870069867-74c57ee1bb07?w=80&q=80", quantity: 1, price: 450000 }
    ],
    total: 1048000,
    shipping: 0,
    discount: 100000,
    address: "456 Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh",
    trackingCode: "JT987654321VN",
    seller: "Fashion House VN",
    paymentMethod: "MoMo",
    estimatedDelivery: "2026-05-09"
  },
  {
    id: "ORD-2026-003",
    date: "2026-04-28",
    status: "delivered",
    items: [
      { productId: "p13", name: "Sách Đắc Nhân Tâm", image: "https://images.unsplash.com/photo-1526406915894-7bcd65f60845?w=80&q=80", quantity: 3, price: 85000 }
    ],
    total: 255000,
    shipping: 15000,
    discount: 0,
    address: "789 Hoàng Diệu, Phường Hải Châu, Quận Hải Châu, Đà Nẵng",
    trackingCode: "GHTK112233445VN",
    seller: "BookWorld VN",
    paymentMethod: "Thẻ ngân hàng",
    estimatedDelivery: "2026-05-02"
  },
  {
    id: "ORD-2026-004",
    date: "2026-04-20",
    status: "cancelled",
    items: [
      { productId: "p4", name: "Samsung Galaxy Watch 6", image: "https://images.unsplash.com/photo-1689287428096-7e1dcc705a5c?w=80&q=80", quantity: 1, price: 4500000, variant: "Gold 44mm" }
    ],
    total: 4500000,
    shipping: 0,
    discount: 0,
    address: "321 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh",
    seller: "TechZone Vietnam",
    paymentMethod: "ZaloPay"
  },
];

export function formatPrice(price: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
}

export const flashSaleEnd = new Date(Date.now() + 6 * 3600 * 1000 + 23 * 60 * 1000 + 45 * 1000);
